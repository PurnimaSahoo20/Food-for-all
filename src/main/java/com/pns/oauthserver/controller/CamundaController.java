package com.pns.oauthserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pns.oauthserver.model.dto.CamundaResponse;
import com.pns.oauthserver.model.dto.CamundaResponseCode;
import com.pns.oauthserver.model.dto.ProcessRequestDto;
import com.pns.oauthserver.service.CamundaService;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.search.response.UserTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import io.camunda.client.api.response.ProcessInstanceEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class CamundaController {

    @Autowired
    private CamundaService processService;


    @GetMapping("/HbtChk")
    public ResponseEntity<Map<String, Object>> heartbeat(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        if(authentication instanceof JwtAuthenticationToken token) {
            response.put("requesting_user", token.getTokenAttributes().get("sub"));
        }
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }


    @PostMapping("/startProcessInstance")
    public ResponseEntity<?> startBpmnProcess(@RequestBody ProcessRequestDto request, Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            if (request.getBpmnId() == null || request.getBpmnId().isEmpty()) {
                return new ResponseEntity<>(
                        new CamundaResponse(CamundaResponseCode.EXCEPTION, "Bpmn Id not found in request body", null),
                        HttpStatus.BAD_REQUEST);
            }
            try {
                long leftLimit = 100000000000L;
                long rightLimit = 1000000000000L;
                Long randomProcessKey = leftLimit + (long) (Math.random() * (rightLimit - leftLimit));

                ProcessInstanceEvent pi = processService.startBpmnProcess(token, request, randomProcessKey);
                CamundaResponse taskSearchResponse;
                if (request.isFastResponseFlag())
                    taskSearchResponse = processService.getTasksByProcessInstanceIdNew(pi.getProcessInstanceKey(), token, randomProcessKey, false);
                else
                    taskSearchResponse = processService.getTasksByProcessInstanceId(pi.getProcessInstanceKey(), token,
                            false);

                CamundaResponse processStartResponse = new CamundaResponse();
                processStartResponse.setStatus(taskSearchResponse.getStatus());
                processStartResponse.setMessage(taskSearchResponse.getMessage());
                Map<String, Object> responseItems = new HashMap<>();
                responseItems.put("processId", pi.getProcessInstanceKey());

                if (request.getFetchVariables() != null && request.getFetchVariables().length > 0) {
                    responseItems.put("processVariables",
                            processService.getProcessVariables(pi.getProcessInstanceKey(), request.getFetchVariables()));
                }

                if (taskSearchResponse.getStatus().equals(CamundaResponseCode.PROCESS_COMPLETED.toString())) {
                    processStartResponse.setItems(responseItems);
                    return new ResponseEntity<>(processStartResponse, HttpStatus.ALREADY_REPORTED);
                }

                if (taskSearchResponse.getStatus().equals(CamundaResponseCode.INCIDENT.toString())) {
                    responseItems.put("incidents", taskSearchResponse.getItems());
                    processStartResponse.setItems(responseItems);
                    return new ResponseEntity<>(processStartResponse, HttpStatus.INTERNAL_SERVER_ERROR);
                }

                if (taskSearchResponse.getStatus().equals(CamundaResponseCode.NO_FURTHER_TASK.toString())) {
                    processStartResponse.setItems(responseItems);
                    return new ResponseEntity<>(processStartResponse, HttpStatus.ALREADY_REPORTED);
                }

                // else return found task(s)
                responseItems.put("userTasks", taskSearchResponse.getItems());
                processStartResponse.setItems(responseItems);
                return new ResponseEntity<>(processStartResponse, HttpStatus.OK);
            } catch (Exception e) {
                log.error("Exception occurred while starting process");
                log.error(e.getMessage());
                e.printStackTrace();
                return new ResponseEntity<>(new CamundaResponse(CamundaResponseCode.EXCEPTION,
                        "Exception occurred in the code" + e.getMessage(), null), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }


    @PostMapping("/completeTask/{taskId}")
    public ResponseEntity<?> completeTask(@PathVariable("taskId") Long taskId, @RequestBody ProcessRequestDto request, Authentication authentication) {


        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                UserTask completedTask = processService.completeTask(taskId, request);
                CamundaResponse taskSearchResponse = new CamundaResponse();
                boolean fetchNextTask = true;
                if (completedTask.getCustomHeaders().get("fetchNextTaskOnComplete") != null && completedTask
                        .getCustomHeaders().get("fetchNextTaskOnComplete").toString().equalsIgnoreCase("false")) {
                    fetchNextTask = Boolean
                            .valueOf(completedTask.getCustomHeaders().get("fetchNextTaskOnComplete").toString());
                }

                if (fetchNextTask && request.isFastResponseFlag())
                    taskSearchResponse = processService
                            .getTasksByProcessInstanceIdNew(completedTask.getProcessInstanceKey(), token, null, false);
                else if (fetchNextTask && !request.isFastResponseFlag())
                    taskSearchResponse = processService.getTasksByProcessInstanceId(completedTask.getProcessInstanceKey(),
                            token, false);

                CamundaResponse completeTaskResponse = new CamundaResponse();
                completeTaskResponse.setStatus(taskSearchResponse.getStatus());
                completeTaskResponse.setMessage(taskSearchResponse.getMessage());
                Map<String, Object> responseItems = new HashMap<>();
                responseItems.put("processId", completedTask.getProcessInstanceKey());

                if (request.getFetchVariables() != null && request.getFetchVariables().length > 0) {
                    responseItems.put("processVariables", processService
                            .getProcessVariables(completedTask.getProcessInstanceKey(), request.getFetchVariables()));
                }

                if (fetchNextTask == false) {
                    completeTaskResponse.setStatus(CamundaResponseCode.SUCCESS.toString());
                    completeTaskResponse.setMessage("Task Completed");
                    completeTaskResponse.setItems(responseItems);
                    return new ResponseEntity<>(completeTaskResponse, HttpStatus.OK);
                }

                if (taskSearchResponse.getStatus().equals(CamundaResponseCode.PROCESS_COMPLETED.toString())) {
                    completeTaskResponse.setItems(responseItems);
                    return new ResponseEntity<>(completeTaskResponse, HttpStatus.ALREADY_REPORTED);
                }

                if (taskSearchResponse.getStatus().equals(CamundaResponseCode.INCIDENT.toString())) {
                    responseItems.put("incidents", taskSearchResponse.getItems());
                    completeTaskResponse.setItems(responseItems);
                    return new ResponseEntity<>(completeTaskResponse, HttpStatus.INTERNAL_SERVER_ERROR);
                }

                if (taskSearchResponse.getStatus().equals(CamundaResponseCode.NO_FURTHER_TASK.toString())) {
                    completeTaskResponse.setItems(responseItems);
                    return new ResponseEntity<>(completeTaskResponse, HttpStatus.ALREADY_REPORTED);
                }

                // else return found task(s)
                responseItems.put("userTasks", taskSearchResponse.getItems());
                completeTaskResponse.setItems(responseItems);
                return new ResponseEntity<>(completeTaskResponse, HttpStatus.OK);
            } catch (Exception e) {
                log.error("Exception occurred while completing task with id {}", taskId);
                log.error(e.getMessage());
                return new ResponseEntity<>(
                        new CamundaResponse(CamundaResponseCode.EXCEPTION,
                                "Exception occurred in the code-->" + e.getMessage(), null),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping(value = "/getFormSchema/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getFormSchema(@PathVariable("taskId") Long taskId, @RequestBody ProcessRequestDto request, Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken) {

            try {
                String formScehmaWithVariables = processService.getFormSchema(taskId);
                return new ResponseEntity<>(formScehmaWithVariables, HttpStatus.OK);
            } catch (Exception e) {
                log.error("Exception occurred while fetching form schema for task with id {}", taskId);
                log.error(e.getMessage());
                return new ResponseEntity<>(
                        new CamundaResponse(CamundaResponseCode.EXCEPTION,
                                "Exception occurred in the code-->" + e.getMessage(), null),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/searchProcessInstances")
    public ResponseEntity<?> fetchActiveProcessesWithTasks(@RequestBody ProcessRequestDto request, Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken token) {
            if (request.getProcessSearchQuery().getPage() != null) {
                if ((request.getProcessSearchQuery().getPage().getFrom() == null ? 0
                        : request.getProcessSearchQuery().getPage().getFrom())
                    + (request.getProcessSearchQuery().getPage().getLimit() == null ? 0
                        : request.getProcessSearchQuery().getPage().getLimit()) > 10000) {
                    return new ResponseEntity<>(
                            new CamundaResponse(CamundaResponseCode.EXCEPTION,
                                    "Request window is too large, from + limit must be less than or equal to: 10000", null),
                            HttpStatus.BAD_REQUEST);
                }

            }

            try {

                CamundaResponse processSearchResponse = processService.fetchProcessesWithTask(request, token);
                return new ResponseEntity<>(processSearchResponse, HttpStatus.OK);
            } catch (Exception e) {
                log.error("Exception occurred while fetching processes");
                e.printStackTrace();
                log.error(e.getMessage());
                return new ResponseEntity<>(
                        new CamundaResponse(CamundaResponseCode.EXCEPTION,
                                "Exception occurred in the code-->" + e.getMessage(), null),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    }

    @PostMapping("/searchProcessInstances/v2")
    public ResponseEntity<?> fetchProcessesV2(@RequestBody ProcessRequestDto request, Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken token) {
            if (request.getProcessSearchQuery().getPage() != null) {
                if ((request.getProcessSearchQuery().getPage().getFrom() == null ? 0
                        : request.getProcessSearchQuery().getPage().getFrom())
                    + (request.getProcessSearchQuery().getPage().getLimit() == null ? 0
                        : request.getProcessSearchQuery().getPage().getLimit()) > 10000) {
                    return new ResponseEntity<>(
                            new CamundaResponse(CamundaResponseCode.EXCEPTION,
                                    "Request window is too large, from + limit must be less than or equal to: 10000", null),
                            HttpStatus.BAD_REQUEST);
                }

            }

            try {
                CamundaResponse processSearchResponse = processService.fetchProcessesV2(request, token);
                return new ResponseEntity<>(processSearchResponse, HttpStatus.OK);
            } catch (Exception e) {
                log.error("Exception occurred while fetching processes");
                e.printStackTrace();
                log.error(e.getMessage());
                return new ResponseEntity<>(
                        new CamundaResponse(CamundaResponseCode.EXCEPTION,
                                "Exception occurred in the code-->" + e.getMessage(), null),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/correlateMessage")
    public ResponseEntity<?> mesageCorrelation(@RequestBody ProcessRequestDto request, Authentication authentication) {

        if (authentication instanceof JwtAuthenticationToken token) {
            try {

                CorrelateMessageResponse cmr = processService.messageCorrelation(request.getMessageCorrelation());
                return new ResponseEntity<>(
                        new CamundaResponse(CamundaResponseCode.SUCCESS, "Correlation Successfull", cmr), HttpStatus.OK);
            } catch (Exception e) {
                log.error("Exception occurred while correlating message");
                e.printStackTrace();
                log.error(e.getMessage());
                return new ResponseEntity<>(
                        new CamundaResponse(CamundaResponseCode.EXCEPTION,
                                "Exception occurred in the code-->" + e.getMessage(), null),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
