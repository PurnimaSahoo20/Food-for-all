package com.pns.oauthserver.job;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CamundaJobWorker {

    @JobWorker(type = "send-notification-volunteers")
    public void sendNotificationToVolunteers(final JobClient client, final ActivatedJob job) {
        log.info("::CamundaJobWorker::sendNotificationToVolunteers");
        log.info("Variables: {}",job.getVariablesAsMap());
    }

    @JobWorker(type = "send-notification-restaurant")
    public void sendNotificationToRestaurant(final JobClient client, final ActivatedJob job) {
        log.info("::CamundaJobWorker::sendNotificationToRestaurant");
    }

}
