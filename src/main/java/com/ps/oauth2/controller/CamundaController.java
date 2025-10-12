package com.ps.oauth2.controller;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ps.oauth2.dto.ProcessRequestDto;
import com.ps.oauth2.service.CamundaService;

import io.camunda.client.api.response.ProcessInstanceEvent;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@RestController
public class CamundaController {
	
	@Autowired
	private CamundaService camundaService;
	
	@PostMapping("/start-process-instance")
	public ResponseEntity<?> startProcess(@RequestBody ProcessRequestDto request, Authentication authentication) throws Exception {
		
		
		
		if(authentication instanceof JwtAuthenticationToken token) {
			log.info("hii{}{}", token,token.getTokenAttributes().get("sub"));
			
			if(request.getBpmnId()==null || request.getBpmnId().trim().length()==0) {
				return ResponseEntity.badRequest().build();
			}
			ProcessInstanceEvent processInstanceEvent=camundaService.startProcess(request.getBpmnId().trim(),token.getTokenAttributes().get("sub").toString());
			camundaService.getTasksByProcessInstanceIdNew(processInstanceEvent.getProcessInstanceKey());
//			Long taskId = 2251799813729624l;
//			camundaService.getFormSchema(taskId);
			
			return ResponseEntity.ok().build();
			
		}
		
		
		
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		
	
		
		
		
	}
	

}
