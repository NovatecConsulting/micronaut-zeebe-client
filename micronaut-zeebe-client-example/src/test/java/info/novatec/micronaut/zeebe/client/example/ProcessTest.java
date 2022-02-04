/*
 * Copyright 2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.novatec.micronaut.zeebe.client.example;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.assertions.ProcessInstanceAssert;
import io.camunda.zeebe.process.test.extensions.ZeebeProcessTest;
import io.camunda.zeebe.process.test.testengine.InMemoryEngine;
import io.camunda.zeebe.process.test.testengine.RecordStreamSource;
import org.junit.jupiter.api.Test;

@ZeebeProcessTest
class ProcessTest {

    InMemoryEngine engine;
    ZeebeClient client;
    @SuppressWarnings("unused")
    RecordStreamSource recordStreamSource;

    @Test
    void workerShouldProcessWork() {

        // Deploy process model
        DeploymentEvent deploymentEvent = client.newDeployCommand()
                .addResourceFromClasspath("bpmn/say_hello.bpmn")
                .send()
                .join();

        BpmnAssert.assertThat(deploymentEvent);

        // Start process instance
        ProcessInstanceEvent event = client.newCreateInstanceCommand()
                .bpmnProcessId("Process_SayHello")
                .latestVersion()
                .send()
                .join();

        engine.waitForIdleState();

        // Verify that process has started
        ProcessInstanceAssert processInstanceAssertions = BpmnAssert.assertThat(event);
        processInstanceAssertions.hasPassedElement("start");
        processInstanceAssertions.isWaitingAtElement("say_hello");

        // Fetch job: say-hello
        ActivateJobsResponse response = client.newActivateJobsCommand()
                .jobType("say-hello")
                .maxJobsToActivate(1)
                .send()
                .join();

        // Complete job: say-hello
        ActivatedJob activatedJob = response.getJobs().get(0);
        client.newCompleteCommand(activatedJob.getKey()).send().join();
        engine.waitForIdleState();

        // Fetch job: say-goodbye
        response = client.newActivateJobsCommand()
                .jobType("say-goodbye")
                .maxJobsToActivate(1)
                .send()
                .join();

        // Complete job: say-goodbye
        activatedJob = response.getJobs().get(0);
        client.newCompleteCommand(activatedJob.getKey()).send().join();
        engine.waitForIdleState();

        // Verify completed
        engine.waitForIdleState();
        processInstanceAssertions.isCompleted();
    }
}
