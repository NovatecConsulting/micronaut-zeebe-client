/*
 * Copyright 2021 original authors
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
package info.novatec.micronaut.zeebe.client.feature;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;


/**
 * @author Martin Sawilla
 * @author Stefan Schultz
 * @author Stephan Seelig
 * @author Tobias Schäfer
 * <p>
 * Allows to configure an external task worker with the {@link ZeebeWorker} annotation. This allows to easily build
 * job workers for multiple topics.
 */
@Context
public class ZeebeWorkerSubscriptionCreator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ZeebeWorkerSubscriptionCreator.class);

    protected final BeanContext beanContext;
    protected final ZeebeClient zeebeClient;
    protected final Configuration configuration;

    protected Collection<JobWorker> jobWorkers = Collections.synchronizedCollection(new ArrayList<>());

    public ZeebeWorkerSubscriptionCreator(BeanContext beanContext, ZeebeClient zeebeClient, Configuration configuration) {
        this.beanContext = beanContext;
        this.zeebeClient = zeebeClient;

        this.configuration = configuration;

        beanContext.getAllBeanDefinitions()
                .parallelStream()
                .forEach(this::registerHandler);
    }

    @PreDestroy
    @Override
    public void close() {
        log.info("Closing {} job workers", jobWorkers.size());
        jobWorkers.forEach(JobWorker::close);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void registerHandler(BeanDefinition<?> beanDefinition) {

        Collection<? extends ExecutableMethod<?, ?>> executableMethods = beanDefinition.getExecutableMethods();
        List<? extends ExecutableMethod<?, ?>> annotatedMethods = executableMethods.stream()
                .filter(m -> m.hasAnnotation(ZeebeWorker.class))
                .collect(Collectors.toList());

        annotatedMethods.parallelStream().forEach(method -> {
            AnnotationValue<ZeebeWorker> annotation = method.getAnnotation(ZeebeWorker.class);
            if (methodSignatureMatchesJobHandler(method.getArguments())) {
                Class<?> declaringType = method.getDeclaringType();
                Object bean = beanContext.getBean(declaringType);
                if (annotation != null) {
                    Optional<String> type = annotation.stringValue("type");
                    type.ifPresent(s -> {
                        JobWorker jobWorker = zeebeClient.newWorker()
                                .jobType(s)
                                .handler((client, job) -> ((ExecutableMethod) method).invoke(bean, client, job))
                                .open();
                        jobWorkers.add(jobWorker);
                        log.info("Zeebe client ({}#{}) subscribed to type '{}'", bean.getClass().getName(), method.getName(), type.get());
                    });
                }
            }
        });
    }

    protected boolean methodSignatureMatchesJobHandler(Argument<?>[] arguments) {
        return arguments.length == 2
                && arguments[0].isAssignableFrom(JobClient.class)
                && arguments[1].isAssignableFrom(ActivatedJob.class);
    }

}
