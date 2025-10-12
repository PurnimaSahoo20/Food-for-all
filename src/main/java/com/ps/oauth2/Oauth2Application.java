package com.ps.oauth2;



import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.JobWorker;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication

public class Oauth2Application {

	@Autowired
	   private MailService senderService;

	public static void main(String[] args) {
		SpringApplication.run(Oauth2Application.class, args);
	}
	
	
	
	
	
	
	private static final Logger log = LogManager.getLogger(Oauth2Application.class);
	


    @JobWorker(type = "send-notification", name = "send-notification-mail")
    public void sendNotificationEmail(final JobClient client, final ActivatedJob job) {
        logJob(job);
        senderService.sendEmail("gobindswain24@gmail.com", "subject", "Hello ");
        client.newCompleteCommand(job.getKey()).send().join();
    }
    
    
    
   
//
    private static void logJob(final ActivatedJob job) {

        log.info(
                "complete job\n>>> [type: {}, key: {}, element: {}, workflow instance: {}]\n{deadline; {}]\n[headers: {}]\n[variables: {}]",
                job.getType(),
                job.getKey(),
                job.getElementId(),
                job.getProcessInstanceKey(),
                Instant.ofEpochMilli(job.getDeadline()),
                job.getCustomHeaders(),
                job.getVariables());
    }

}
