package com.ps.oauth2.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.ps.oauth2.dto.CamundaResponse;
import com.ps.oauth2.dto.CamundaResponseCode;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.Form;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.filter.IncidentFilterImpl;
import io.camunda.client.impl.search.filter.UserTaskFilterImpl;
import io.camunda.client.impl.search.filter.VariableFilterImpl;
import io.camunda.client.impl.search.response.UserTaskImpl;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.protocol.rest.UserTaskStateEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CamundaService {
	@Autowired
	CamundaClient camundaClient;
	
	public ProcessInstanceEvent startBpmnProcess(String bpmnId, String userName) {
		
		
		
		Map<String, Object> variables = new HashMap<>();
		variables.put("userTaskAssignee", userName);
		ProcessInstanceEvent processInstanceEvent = camundaClient.newCreateInstanceCommand()
                                                                  
				.bpmnProcessId(bpmnId).latestVersion().variables(variables).send().join();
        Long processId = processInstanceEvent.getProcessInstanceKey();
		log.info("Process started with Id: {}",processId);
		
		return processInstanceEvent;
	}
	
	public void getTaskByProcessInstanceId(Long processId) {
		List<UserTask> userTasks = new ArrayList<>();
		 UserTaskFilter userAssignedTaskFilter = new UserTaskFilterImpl().processInstanceKey(processId)
	                .state(UserTaskState.CREATED).assignee("user1");
		 
		 camundaClient.newUserTaskSearchRequest().filter(userAssignedTaskFilter).send().join().items();
		 
//		 userTasks.addAll(
//                 camundaClient.newUserTaskSearchRequest().filter(userAssignedTaskFilter).send().join().items());
		 log.info("hhi{}",userTasks);
	}
	
	 public CamundaResponse getTasksByProcessInstanceIdNew(Long processInstanceId
           ) throws Exception {
		 
		    long leftLimit = 100000000000L;
		    long rightLimit = 1000000000000L;
			Long randomProcessKey = leftLimit + (long) (Math.random() * (rightLimit - leftLimit));
			
			boolean isProcessStartCall=true;

            log.info("Fetching tasks for process id:{}", processInstanceId);
            ObjectMapper objectMapper = new ObjectMapper();

            List<UserTask> resultTaskList = new ArrayList<>();
            List<ActivatedJob> activatedJobs = new ArrayList<>();
            List<Incident> incidents = new ArrayList<>();
            String userInSession ="user1";
            List<String> userInSession_roles_priv = new ArrayList<>();


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
                  if (!taskAssignee.equals(userInSession) && !taskCandidateUsers.contains(userInSession)&& Collections.disjoint(set1, set2)) {
                                                                                               
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
                          return new CamundaResponse(CamundaResponseCode.NO_FURTHER_TASK, "No new task(s) found assigned to the user",resultTaskList);
           }
           log.info("Tasks fetched for process id:{}", processInstanceId);
           return new CamundaResponse(CamundaResponseCode.EXISTING_NEW_TASK, "New task(s) found assigned to the user",resultTaskList);
       }
	 
//	   public String getFormSchema(Long taskId) throws Exception {
//	        log.info("Fetching form schema for taskId {}", taskId);
//	        UserTask userTask = camundaClient.newUserTaskGetRequest(taskId).send().join();
//	        Object formSchema = "";
//	        Form form = camundaClient.newUserTaskGetFormRequest(taskId).send().join();
//	        if (form != null) {
//	            formSchema = form.getSchema();
//	        } else {
//	            formSchema = userTask.getExternalFormReference();
//	        }
//	        JSONObject variables = new JSONObject();
//	        List<Variable> variablesList = camundaClient.newUserTaskVariableSearchRequest(taskId).send().join().items();
//	        ObjectMapper objectMapper = new ObjectMapper();
//	        for (Variable var : variablesList) {
//	            if (var.isTruncated()) {
//	                String varFullvalue = camundaClient.newVariableGetRequest(var.getVariableKey()).send().join().getValue();
//	                variables.put(var.getName(), objectMapper.readValue(varFullvalue, Object.class));
//	            } else
//	                variables.put(var.getName(), objectMapper.readValue(var.getValue(), Object.class));
//	        }
//
//	        // checking and fetching if required variables are not present
//	        List<String> requiredVariables = new ArrayList<>();
//	        try {
//	            List<Object> list = (List<Object>) objectMapper.readValue((String) formSchema, Map.class).get("components");
//	            requiredVariables = objectMapper
//	                    .readValue((String) ((Map<String, Object>) ((Map<String, Object>) list.get(0)).get("properties"))
//	                            .get("requiredInputVariables"), List.class);
//	        } catch (Exception e) {
//	        }
//	        for (String reqVar : requiredVariables) {
//	            while (true) {
//	                try {
//	                    variables.get(reqVar); // will throw exception if reqVar is not present
//	                    break;
//	                } catch (Exception e) {
//	                    VariableFilter vf = new VariableFilterImpl().processInstanceKey(userTask.getProcessInstanceKey())
//	                            .name(reqVar);
//	                    List<Variable> vars = camundaClient.newVariableSearchRequest().filter(vf).send().join().items();
//	                    if (vars.isEmpty()) {
//	                        continue;
//	                    }
//	                    Variable v = vars.get(0);
//	                    if (v.isTruncated()) {
//	                        String varFullvalue = camundaClient.newVariableGetRequest(v.getVariableKey()).send().join().getValue();
//	                        variables.put(v.getName(), objectMapper.readValue(varFullvalue, Object.class));
//	                    } else
//	                        variables.put(v.getName(), objectMapper.readValue(v.getValue(), Object.class));
//	                }
//	            }
//
//	        }
//
//	        String result = "{" + "\"form\":" + formSchema + "," + "\"processVariables\":" + variables + "}";
//	        log.info("Form schema fetched for taskId {}", taskId);
//	        return result;
//	    }


}
