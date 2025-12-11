package com.pns.oauthserver.model.dto;

import io.camunda.client.protocol.rest.MessageCorrelationRequest;
import io.camunda.client.protocol.rest.ProcessInstanceSearchQuery;
import lombok.Data;

import java.util.Map;

@Data
public class ProcessRequestDto {

    private String tenantId;
    private String applicationSource;
    private String taskAssignee;
    private String bpmnId;
    private String processDefinitionKey;
    private String processInstanceKey;
    private Map<String,Object> processVariables;
    private boolean fastResponseFlag = false;
    private String[] fetchVariables;
    private ProcessInstanceSearchQuery processSearchQuery;
    MessageCorrelationRequest messageCorrelation;
    

}
