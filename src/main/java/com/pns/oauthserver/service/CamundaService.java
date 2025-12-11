package com.pns.oauthserver.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pns.oauthserver.model.dto.CamundaResponse;
import com.pns.oauthserver.model.dto.CamundaResponseCode;
import com.pns.oauthserver.model.dto.ProcessRequestDto;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.*;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.http.HttpClientFactory;
import io.camunda.client.impl.search.filter.IncidentFilterImpl;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskFilterImpl;
import io.camunda.client.impl.search.filter.VariableFilterImpl;
import io.camunda.client.impl.search.request.SearchRequestPageImpl;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.impl.search.response.UserTaskImpl;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.protocol.rest.ProcessInstanceSearchQuerySortRequest.FieldEnum;
import org.apache.hc.client5.http.config.RequestConfig;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CamundaService {

    private final Logger log = LoggerFactory.getLogger(CamundaService.class);

    private final JsonMapper jsonMapper;

    public CamundaService(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Autowired
    private CamundaClient camundaClient;

    public ProcessInstanceEvent startBpmnProcess(JwtAuthenticationToken jwtToken, ProcessRequestDto reqDto, Long randomProcessKey) throws Exception {
        log.info("::CamundaProcessServiceImpl::starting bpmn process::");
        HashMap<String, Object> variables = new HashMap<>();
        variables.put("applicationSource", reqDto.getApplicationSource());
        variables.put("userTaskAssignee", jwtToken.getTokenAttributes().get("sub"));
        log.info("user:{}",jwtToken.getTokenAttributes().get("sub"));
        variables.put("tenantId", "ffa");
        variables.put("randomProcessKey", randomProcessKey);
        List<String> roles = (List<String>) jwtToken.getTokenAttributes().get("role");
        variables.put("role", String.join(",", roles));
        variables.put("restaurantRegion", jwtToken.getTokenAttributes().get("region"));
        variables.put("volunteer_status", "Initiated");

        if (reqDto.getProcessVariables() != null) {
            for (Map.Entry<String, Object> entry : reqDto.getProcessVariables().entrySet()) {
                variables.put(entry.getKey(), entry.getValue());
            }
        }
        if (reqDto.getProcessVariables() != null) {
            for (Map.Entry<String, Object> entry : reqDto.getProcessVariables().entrySet()) {
                variables.put(entry.getKey(), entry.getValue());
            }
        }

        ProcessInstanceEvent processInstanceEvent = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(reqDto.getBpmnId()).latestVersion().variables(variables).send().join();
        Long processId = processInstanceEvent.getProcessInstanceKey();
        HashMap<String, Object> additionalVariables = new HashMap<>();
        additionalVariables.put("processId", processId);
        camundaClient.newSetVariablesCommand(processId).variables(additionalVariables).send();

        log.info("process instance created with id {}", processId);
        return processInstanceEvent;

    }

    public UserTask completeTask(Long taskId, ProcessRequestDto reqDto)  {
        HashMap<String, Object> variables = new HashMap<>();
        if (reqDto.getProcessVariables() != null) {
            variables.putAll(reqDto.getProcessVariables());
        }
        camundaClient.newCompleteUserTaskCommand(taskId).variables(variables).send().join();
        UserTask completedTask = camundaClient.newUserTaskGetRequest(taskId).send().join();
        while (!completedTask.getState().equals(UserTaskState.COMPLETED)) {
            completedTask = camundaClient.newUserTaskGetRequest(taskId).send().join();
        }
        log.info("task with id {} completed for process instance {}", taskId, completedTask.getProcessInstanceKey());
        return completedTask;

    }

    public String getFormSchema(Long taskId) throws Exception {
        log.info("Fetching form schema for taskId {}", taskId);
        UserTask userTask = camundaClient.newUserTaskGetRequest(taskId).send().join();
        Object formSchema = "";
        Form form = camundaClient.newUserTaskGetFormRequest(taskId).send().join();
        if (form != null) {
            formSchema = form.getSchema();
        } else {
            formSchema = userTask.getExternalFormReference();
        }
        JSONObject variables = new JSONObject();
        List<Variable> variablesList = camundaClient.newUserTaskVariableSearchRequest(taskId).send().join().items();
        ObjectMapper objectMapper = new ObjectMapper();
        for (Variable var : variablesList) {
            if (var.isTruncated()) {
                String varFullvalue = camundaClient.newVariableGetRequest(var.getVariableKey()).send().join().getValue();
                variables.put(var.getName(), objectMapper.readValue(varFullvalue, Object.class));
            } else
                variables.put(var.getName(), objectMapper.readValue(var.getValue(), Object.class));
        }

        // checking and fetching if required variables are not present
        List<String> requiredVariables = new ArrayList<>();
        try {
            List<Object> list = (List<Object>) objectMapper.readValue((String) formSchema, Map.class).get("components");
            requiredVariables = objectMapper
                    .readValue((String) ((Map<String, Object>) ((Map<String, Object>) list.get(0)).get("properties"))
                            .get("requiredInputVariables"), List.class);
        } catch (Exception e) {
        }
        for (String reqVar : requiredVariables) {
            while (true) {
                try {
                    variables.get(reqVar); // will throw exception if reqVar is not present
                    break;
                } catch (Exception e) {
                    VariableFilter vf = new VariableFilterImpl().processInstanceKey(userTask.getProcessInstanceKey())
                            .name(reqVar);
                    List<Variable> vars = camundaClient.newVariableSearchRequest().filter(vf).send().join().items();
                    if (vars.isEmpty()) {
                        continue;
                    }
                    Variable v = vars.get(0);
                    if (v.isTruncated()) {
                        String varFullvalue = camundaClient.newVariableGetRequest(v.getVariableKey()).send().join().getValue();
                        variables.put(v.getName(), objectMapper.readValue(varFullvalue, Object.class));
                    } else
                        variables.put(v.getName(), objectMapper.readValue(v.getValue(), Object.class));
                }
            }

        }

        String result = "{" + "\"form\":" + formSchema + "," + "\"processVariables\":" + variables + "}";
        log.info("Form schema fetched for taskId {}", taskId);
        return result;
    }

    public CamundaResponse fetchProcessesWithTask(ProcessRequestDto request, JwtAuthenticationToken token) throws Exception {
        log.info("Searching processes for given query");
        HttpClient httpClient = new HttpClientFactory(camundaClient.getConfiguration()).createClient();
        httpClient.start();

        RequestConfig.Builder httpRequestConfig = httpClient.newRequestConfig();
        final HttpCamundaFuture<SearchResponse<ProcessInstance>> result = new HttpCamundaFuture<>();
        httpClient.post("/process-instances/search", jsonMapper.toJson(request.getProcessSearchQuery()),
                httpRequestConfig.build(), ProcessInstanceSearchQueryResult.class,
                SearchResponseMapper::toProcessInstanceSearchResponse, result);
        List<ProcessInstance> processInstances = result.join().items();
        SearchResponsePage srp = result.join().page();

        httpClient.close();

        if (processInstances.isEmpty()) {
            return new CamundaResponse(CamundaResponseCode.NO_PROCESS_INSTANCE, "No processes found for given query",
                    null);
        }

        List<Map<String, Object>> processDetailsList = new ArrayList<>();
        processInstances.parallelStream().forEach(pi -> {
            try {
                Map<String, Object> processDetails = new HashMap<>();
                processDetails.put("processDetail", pi);

                // fetch requested variables
                if (request.getFetchVariables() != null && request.getFetchVariables().length > 0) {
                    processDetails.put("processVariables",
                            getProcessVariables(pi.getProcessInstanceKey(), request.getFetchVariables()));
                }

                // fetch process tasks
                if (pi.getState().equals(ProcessInstanceState.ACTIVE)) {
                    CamundaResponse taskSearchResponse = getTasksByProcessInstanceId(pi.getProcessInstanceKey(),
                            token, true);
                    if (taskSearchResponse.getStatus().equals(CamundaResponseCode.EXISTING_NEW_TASK.toString())) {
                        processDetails.put("userTasks", taskSearchResponse.getItems());
                    } else if (taskSearchResponse.getStatus().equals(CamundaResponseCode.INCIDENT.toString())) {
                        processDetails.put("userTasks", null);
                        processDetails.put("incidents", taskSearchResponse.getItems());
                    } else {
                        // when found no user task for user in the session and no incidents
                        processDetails.put("userTasks", null);
                        processDetails.put("incidents", null);
                    }
                    processDetailsList.add(processDetails);
                } else
                    processDetailsList.add(processDetails); // for process completed or terminated

            } catch (Exception e) {
                log.info("Exception occurred while fetching details of processId {}", pi.getProcessInstanceKey());
                e.printStackTrace();
            }
        });

        if (processDetailsList.isEmpty()) {
            return new CamundaResponse(CamundaResponseCode.NO_PROCESS_INSTANCE,
                    "No processes with associated tasks found for given query", null);
        }

        log.info("Found processes for given query");
        //sorting on process start date
        if (!request.getProcessSearchQuery().getSort().isEmpty()) {
            SortOrderEnum stDtSortOrder = null;
            for (ProcessInstanceSearchQuerySortRequest sort : request.getProcessSearchQuery().getSort()) {
                if (sort.getField().equals(FieldEnum.START_DATE)) {
                    stDtSortOrder = sort.getOrder();
                    break;
                }
            }
            SortOrderEnum startDateSortOrder = stDtSortOrder != null ? stDtSortOrder : SortOrderEnum.ASC;

            Collections.sort(processDetailsList, new Comparator<Map<String, Object>>() {
                public int compare(final Map<String, Object> o1, final Map<String, Object> o2) {
                    if (startDateSortOrder.equals(SortOrderEnum.DESC))
                        return ((((ProcessInstance) o2.get("processDetail")).getStartDate())).compareTo((((ProcessInstance) o1.get("processDetail")).getStartDate()));
                    else
                        return ((((ProcessInstance) o1.get("processDetail")).getStartDate())).compareTo((((ProcessInstance) o2.get("processDetail")).getStartDate()));
                }
            });
        }

        CamundaResponse response = new CamundaResponse(CamundaResponseCode.FOUND_PROCESS_INSTANCE, "Found processes for given query",
                processDetailsList);
        Map<String, Object> pageDetails = new HashMap<>();
        pageDetails.put("totalItems", srp.totalItems());
        pageDetails.put("startCursor", srp.startCursor());
        pageDetails.put("endCursor", srp.endCursor());
        response.setPage(pageDetails);
        return response;
    }


    @SuppressWarnings("unchecked")
    public CamundaResponse fetchProcessesV2(ProcessRequestDto request, JwtAuthenticationToken token) throws Exception {
        log.info("Searching processes for given query");
        HttpClient httpClient = new HttpClientFactory(camundaClient.getConfiguration()).createClient();
        httpClient.start();

        RequestConfig.Builder httpRequestConfig = httpClient.newRequestConfig();
        final HttpCamundaFuture<SearchResponse<ProcessInstance>> result = new HttpCamundaFuture<>();
        httpClient.post("/process-instances/search", jsonMapper.toJson(request.getProcessSearchQuery()),
                httpRequestConfig.build(), ProcessInstanceSearchQueryResult.class,
                SearchResponseMapper::toProcessInstanceSearchResponse, result);
        List<ProcessInstance> processInstances = result.join().items();
        SearchResponsePage srp = result.join().page();

        httpClient.close();

        if (processInstances.isEmpty()) {
            return new CamundaResponse(CamundaResponseCode.NO_PROCESS_INSTANCE, "No processes found for given query",
                    null);
        }

        List<Map<String, Object>> processDetailsList = new ArrayList<>();
        List<Long> pIds = processInstances.stream().map(m -> m.getProcessInstanceKey()).collect(Collectors.toList());

        for (String reqVarName : request.getFetchVariables()) {
            VariableFilter vf = new VariableFilterImpl().processInstanceKey(f -> f.in(pIds)).name(reqVarName);
            ObjectMapper objectMapper = new ObjectMapper();
            List<Variable> varList = camundaClient.newVariableSearchRequest().filter(vf)
                    .page(new SearchRequestPageImpl().limit(10000)).send().join().items();

            for (Variable var : varList) {
                Map<String, Object> processDetail = new HashMap<>();
                Map<String, Object> variables = new HashMap<>();
                Optional<Map<String, Object>> existingPD = processDetailsList.stream()
                        .filter(m -> m.get("processId").equals(var.getProcessInstanceKey())).findAny();

                Object varValue = null;
                try {
                    if (var.isTruncated()) {
                        String varFullvalue = camundaClient.newVariableGetRequest(var.getVariableKey()).send().join()
                                .getValue();
                        varValue = objectMapper.readValue(varFullvalue, Object.class);
                    } else
                        varValue = objectMapper.readValue(var.getValue(), Object.class);

                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                if (existingPD.isPresent()) {
                    int index = processDetailsList.indexOf(existingPD.get());
                    processDetail = processDetailsList.get(index);
                    variables = (Map<String, Object>) processDetail.get("processVariables");
                    variables.put(var.getName(), varValue);
                } else {
                    variables.put(var.getName(), varValue);
                    processDetail.put("processId", var.getProcessInstanceKey());
                    ProcessInstance pi = processInstances.stream().filter(m -> m.getProcessInstanceKey().equals(var.getProcessInstanceKey())).findAny().get();
                    processDetail.put("processDetail", pi);
                    processDetail.put("processVariables", variables);
                    processDetailsList.add(processDetail);
                }
            }
        }

        //if no fetchVariable was provided in request body
        if (processDetailsList.isEmpty()) {
            processInstances.stream().forEach(pi -> {
                Map<String, Object> processDetail = new HashMap<>();
                Map<String, Object> variables = new HashMap<>();
                processDetail.put("processId", pi.getProcessInstanceKey());
                processDetail.put("processDetail", pi);
                processDetail.put("processVariables", variables);
                processDetailsList.add(processDetail);
            });
        }

        //sorting on process start date
        if (!request.getProcessSearchQuery().getSort().isEmpty()) {
            SortOrderEnum stDtSortOrder = null;
            for (ProcessInstanceSearchQuerySortRequest sort : request.getProcessSearchQuery().getSort()) {
                if (sort.getField().equals(FieldEnum.START_DATE)) {
                    stDtSortOrder = sort.getOrder();
                    break;
                }
            }
            SortOrderEnum startDateSortOrder = stDtSortOrder != null ? stDtSortOrder : SortOrderEnum.ASC;

            Collections.sort(processDetailsList, new Comparator<Map<String, Object>>() {
                public int compare(final Map<String, Object> o1, final Map<String, Object> o2) {
                    if (startDateSortOrder.equals(SortOrderEnum.DESC))
                        return ((((ProcessInstance) o2.get("processDetail")).getStartDate())).compareTo((((ProcessInstance) o1.get("processDetail")).getStartDate()));
                    else
                        return ((((ProcessInstance) o1.get("processDetail")).getStartDate())).compareTo((((ProcessInstance) o2.get("processDetail")).getStartDate()));
                }
            });
        }


        log.info("Found processes for given query");
        CamundaResponse response = new CamundaResponse(CamundaResponseCode.FOUND_PROCESS_INSTANCE,
                "Found processes for given query", processDetailsList);
        Map<String, Object> pageDetails = new HashMap<>();
        pageDetails.put("totalItems", srp.totalItems());
        pageDetails.put("startCursor", srp.startCursor());
        pageDetails.put("endCursor", srp.endCursor());
        response.setPage(pageDetails);
        return response;
    }

    public CorrelateMessageResponse messageCorrelation(MessageCorrelationRequest req) throws Exception {

        CorrelateMessageCommandStep3 corrMsgCommand = camundaClient.newCorrelateMessageCommand()
                .messageName(req.getName()).correlationKey(req.getCorrelationKey());
        if (req.getVariables() != null) {
            corrMsgCommand.variables(req.getVariables());
        }
        if (req.getTenantId() != null) {
            corrMsgCommand.variables(req.getTenantId());
        }
        CorrelateMessageResponse cmr = corrMsgCommand.send().join();
        return cmr;

    }

    public CamundaResponse getTasksByProcessInstanceId(Long processInstanceId, JwtAuthenticationToken authToken,
                                                       boolean isProcessSearchCall) {
        log.info("Fetching tasks for process id:{}", processInstanceId);
        List<UserTask> userAssignedTasks = new ArrayList<>();
        List<UserTask> candidateUserAssignedTasks = new ArrayList<>();
        List<UserTask> groupAssignedTasks = new ArrayList<>();
        List<Incident> incidents = new ArrayList<>();
        List<String> roles_priv = new ArrayList<>();
        List<ProcessInstance> finishedPi = new ArrayList<>();
        String userInSession = (String) authToken.getTokenAttributes().get("sub");
        roles_priv.addAll((List<String>) authToken.getTokenAttributes().get("role"));

        UserTaskFilter userAssignedTaskFilter = new UserTaskFilterImpl().processInstanceKey(processInstanceId)
                .state(UserTaskState.CREATED).assignee(b -> b.exists(true));
//        UserTaskFilter candidateUserAssignedTaskFilter = new UserTaskFilterImpl().processInstanceKey(processInstanceId)
//                .state(UserTaskState.CREATED).candidateUser(b -> b.exists(true));
        UserTaskFilter groupAssignedTaskFilter = new UserTaskFilterImpl().processInstanceKey(processInstanceId)
                .state(UserTaskState.CREATED).candidateGroup(b -> b.exists(true));

        IncidentFilter ifilter = new IncidentFilterImpl().processInstanceKey(processInstanceId);
        ProcessInstanceFilter pfilter = new ProcessInstanceFilterImpl().processInstanceKey(processInstanceId)
                .state(ProcessInstanceState.COMPLETED);

//		ExecutorService executor = Executors.newFixedThreadPool(4);
//		while (finishedPi.isEmpty() && groupAssignedTasks.isEmpty() && userAssignedTasks.isEmpty()
//				&& incidents.isEmpty()) {
//			groupAssignedTasks.addAll(CompletableFuture.supplyAsync(
//					() -> camundaClient.newUserTaskQuery().filter(groupAssignedTaskFilter).send().join().items(),
//					executor).get());
//			userAssignedTasks.addAll(CompletableFuture.supplyAsync(
//					() -> camundaClient.newUserTaskQuery().filter(userAssignedTaskFilter).send().join().items(),
//					executor).get());
//			incidents.addAll(CompletableFuture
//					.supplyAsync(() -> camundaClient.newIncidentQuery().filter(ifilter).send().join().items(), executor)
//					.get());
//			finishedPi.addAll(CompletableFuture
//					.supplyAsync(() -> camundaClient.newProcessInstanceQuery().filter(pfilter).send().join().items(),
//							executor)
//					.get());
//		 if(isProcessSearchCall){
//				break;
//		  }
//		}
        while (finishedPi.isEmpty() && groupAssignedTasks.isEmpty() && userAssignedTasks.isEmpty()
               && incidents.isEmpty()) {

            groupAssignedTasks.addAll(
                    camundaClient.newUserTaskSearchRequest().filter(groupAssignedTaskFilter).send().join().items());
            userAssignedTasks.addAll(
                    camundaClient.newUserTaskSearchRequest().filter(userAssignedTaskFilter).send().join().items());
            // candidateUserAssignedTasks.addAll(camundaClient.newUserTaskSearchRequest()
            // .filter(candidateUserAssignedTaskFilter).send().join().items());
            incidents.addAll(camundaClient.newIncidentSearchRequest().filter(ifilter).send().join().items());
            finishedPi.addAll(camundaClient.newProcessInstanceSearchRequest().filter(pfilter).send().join().items());
            if (isProcessSearchCall) {
                break;
            }
        }

        if (finishedPi.isEmpty() == false) {
            // process completed, no task to fetch
            log.info("Process with id {} has been already completed", processInstanceId);
            return new CamundaResponse(CamundaResponseCode.PROCESS_COMPLETED, "Process has Completed", null);
        }

        if (incidents.isEmpty() == false) {
            // incident occured in process workflow
            log.info("Incidents found in workflow for process id:{}-->{}", processInstanceId,
                    incidents.get(0).getErrorMessage());
            return new CamundaResponse(CamundaResponseCode.INCIDENT,
                    "Incident occured in the process workflow for process id:" + processInstanceId, incidents);
        }

        // else there will be new tasks in the process
        List<UserTask> resultTaskList = new ArrayList<>();
        List<Long> resultTaskIdList = new ArrayList<>();
        if (groupAssignedTasks.isEmpty()) {
            groupAssignedTasks = camundaClient.newUserTaskSearchRequest()
                    .filter(new UserTaskFilterImpl().processInstanceKey(processInstanceId).state(UserTaskState.CREATED)
                            .candidateGroup(b -> b.in(roles_priv)))
                    .send().join().items();
        }
        if (userAssignedTasks.isEmpty()) {
            userAssignedTasks = camundaClient.newUserTaskSearchRequest().filter(new UserTaskFilterImpl()
                            .processInstanceKey(processInstanceId).state(UserTaskState.CREATED).assignee(userInSession)).send()
                    .join().items();
        }
        if (candidateUserAssignedTasks.isEmpty()) {
            candidateUserAssignedTasks = camundaClient
                    .newUserTaskSearchRequest().filter(new UserTaskFilterImpl().processInstanceKey(processInstanceId)
                            .state(UserTaskState.CREATED).candidateUser(b -> b.eq(userInSession)))
                    .send().join().items();
        }

        for (
                UserTask ut : userAssignedTasks) {
            if (ut.getAssignee().equals(userInSession)) {
                resultTaskList.add(ut);
                resultTaskIdList.add(ut.getUserTaskKey());
            }
        }
        for (
                UserTask ut : candidateUserAssignedTasks) {
            if (ut.getCandidateUsers().contains(userInSession) && !resultTaskIdList.contains(ut.getUserTaskKey())) {
                resultTaskList.add(ut);
                resultTaskIdList.add(ut.getUserTaskKey());
            }
        }
        for (
                UserTask ut : groupAssignedTasks) {
            Set<String> set1 = new HashSet<>(roles_priv);
            Set<String> set2 = new HashSet<>(ut.getCandidateGroups());
            boolean isSubset = set1.containsAll(set2);
            if (isSubset && !resultTaskIdList.contains(ut.getUserTaskKey())) {
                resultTaskList.add(ut);
                resultTaskIdList.add(ut.getUserTaskKey());
            }
        }

        if (resultTaskList.isEmpty()) {
            return new CamundaResponse(CamundaResponseCode.NO_FURTHER_TASK, "No new task(s) found assigned to the user",
                    resultTaskList);
        }
        log.info("Tasks fetched for process id:{}", processInstanceId);
        return new

                CamundaResponse(CamundaResponseCode.EXISTING_NEW_TASK, "New task(s) found assigned to the user",
                resultTaskList);
    }

    public CamundaResponse getTasksByProcessInstanceIdNew(Long processInstanceId, JwtAuthenticationToken authToken,
                                                          Long randomProcessKey, boolean isProcessStartCall) throws Exception {
        log.info("Fetching tasks for process id:{}", processInstanceId);
        List<UserTask> resultTaskList = new ArrayList<>();
        List<ActivatedJob> activatedJobs = new ArrayList<>();
        List<Incident> incidents = new ArrayList<>();
        String userInSession = (String) authToken.getTokenAttributes().get("sub");
        List<String> userInSession_roles_priv = new ArrayList<>();
        userInSession_roles_priv.addAll((List<String>) authToken.getTokenAttributes().get("role"));

        if (randomProcessKey == null) {
            VariableFilter vf = new VariableFilterImpl().processInstanceKey(processInstanceId).name("randomProcessKey");
            randomProcessKey = Long.valueOf(
                    camundaClient.newVariableSearchRequest().filter(vf).send().join().items().get(0).getValue());
        }

        IncidentFilter ifilter = new IncidentFilterImpl().processInstanceKey(processInstanceId);
        while (activatedJobs.isEmpty() && incidents.isEmpty()) {
            activatedJobs = camundaClient.newActivateJobsCommand()
                    .jobType("fetchTasksJob-" + String.valueOf(randomProcessKey)).maxJobsToActivate(10)
                    .timeout(Duration.ofSeconds(60)).requestTimeout(Duration.ofMillis(500)).send().join().getJobs();
            incidents = camundaClient.newIncidentSearchRequest().filter(ifilter).send().join().items();
        }

        if (incidents.isEmpty() == false) {
            // incident occured in process workflow
            log.info("Incidents found in workflow for process id:{}-->{}", processInstanceId,
                    incidents.get(0).getErrorMessage());
            return new CamundaResponse(CamundaResponseCode.INCIDENT,
                    "Incident occured in the process workflow for process id:" + processInstanceId, incidents);
        }

        if (!activatedJobs.isEmpty() && activatedJobs.get(0).getElementId().equals("Process_End")) {
            // process completed, no task to fetch
            log.info("Process with id {} has been already completed", processInstanceId);
            camundaClient.newCompleteCommand(activatedJobs.get(0)).send();
            return new CamundaResponse(CamundaResponseCode.PROCESS_COMPLETED, "Process has Completed", null);
        }

        if (!activatedJobs.isEmpty() && activatedJobs.get(0).getCustomHeaders().get("parallelUserTasks") != null) {
            Integer parallelUserTasks = Integer
                    .valueOf(activatedJobs.get(0).getCustomHeaders().get("parallelUserTasks"));
            while (activatedJobs.size() <= parallelUserTasks) {
                activatedJobs.addAll(camundaClient.newActivateJobsCommand()
                        .jobType("fetchTasksJob-" + String.valueOf(randomProcessKey)).maxJobsToActivate(10)
                        .timeout(Duration.ofSeconds(60)).requestTimeout(Duration.ofMillis(500)).send().join()
                        .getJobs());
            }
        }

        for (ActivatedJob job : activatedJobs) {
            List<String> taskCandidateGroups = new ArrayList<>();
            List<String> taskCandidateUsers = new ArrayList<>();
            String taskAssignee = job.getCustomHeaders().get("io.camunda.zeebe:assignee");
            String candidateGroupsStr = job.getCustomHeaders().get("io.camunda.zeebe:candidateGroups");
            if (candidateGroupsStr != null) {
                taskCandidateGroups = Arrays.asList(candidateGroupsStr.substring(1, candidateGroupsStr.length() - 1)
                        .replaceAll("\"", "").split(","));
            }

            String candidateUsersStr = job.getCustomHeaders().get("io.camunda.zeebe:candidateUsers");
            if (candidateUsersStr != null) {
                taskCandidateUsers = Arrays.asList(
                        candidateUsersStr.substring(1, candidateUsersStr.length() - 1).replaceAll("\"", "").split(","));
            }

            Set<String> set1 = new HashSet<>(taskCandidateGroups);
            Set<String> set2 = new HashSet<>(userInSession_roles_priv);
            if (!taskAssignee.equals(userInSession) && !taskCandidateUsers.contains(userInSession)
                && Collections.disjoint(set1, set2)) {
                camundaClient.newCompleteCommand(job).send();
                continue;
            }

            Instant now = Instant.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);
            String formattedCurrentTimestamp = formatter.format(now);
            UserTaskResult task = new UserTaskResult()
                    .userTaskKey(job.getCustomHeaders().get("io.camunda.zeebe:userTaskKey")).state(UserTaskStateEnum.CREATED)
                    .assignee(taskAssignee).elementId(job.getElementId())
                    .elementInstanceKey(String.valueOf(job.getElementInstanceKey()))
                    .processDefinitionId(job.getBpmnProcessId())
                    .processInstanceKey(String.valueOf(job.getProcessInstanceKey()))
                    .formKey(job.getCustomHeaders().get("io.camunda.zeebe:formKey"))
                    .creationDate(formattedCurrentTimestamp)
                    .followUpDate(job.getCustomHeaders().get("io.camunda.zeebe:followUpDate"))
                    .dueDate(job.getCustomHeaders().get("io.camunda.zeebe:dueDate")).tenantId(job.getTenantId())
                    .processDefinitionVersion(job.getProcessDefinitionVersion())
                    .customHeaders(job.getCustomHeaders().entrySet().stream().filter(a -> !a.getKey().startsWith("io"))
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())))
                    .priority(Integer.valueOf(job.getCustomHeaders().get("io.camunda.zeebe:priority")))
                    .candidateGroups(taskCandidateGroups).candidateUsers(taskCandidateUsers);
            resultTaskList.add(new UserTaskImpl(task));
            camundaClient.newCompleteCommand(job).send();
        }

        if (resultTaskList.isEmpty()) {
            return new CamundaResponse(CamundaResponseCode.NO_FURTHER_TASK, "No new task(s) found assigned to the user",
                    resultTaskList);
        }
        log.info("Tasks fetched for process id:{}", processInstanceId);
        return new CamundaResponse(CamundaResponseCode.EXISTING_NEW_TASK, "New task(s) found assigned to the user",
                resultTaskList);
    }

    public Map<String, Object> getProcessVariables(long processInstanceId, String[] requestedVariables)
            throws Exception {

        VariableFilter vf = new VariableFilterImpl().processInstanceKey(processInstanceId)
                .name(f -> f.in(requestedVariables));
        Map<String, Object> variables = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        for (Variable var : camundaClient.newVariableSearchRequest().filter(vf).send().join().items()) {
            if (var.isTruncated()) {
                String varFullvalue = camundaClient.newVariableGetRequest(var.getVariableKey()).send().join().getValue();
                variables.put(var.getName(), objectMapper.readValue(varFullvalue, Object.class));
            } else
                variables.put(var.getName(), objectMapper.readValue(var.getValue(), Object.class));
        }
        return variables;
    }

}
