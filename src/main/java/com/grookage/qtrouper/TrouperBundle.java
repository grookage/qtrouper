/*
 * Copyright 2019 Koushik R <rkoushik.14@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.grookage.qtrouper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.grookage.qtrouper.core.healthchecks.TrouperHealthCheck;
import com.grookage.qtrouper.core.rabbit.RabbitConfiguration;
import com.grookage.qtrouper.core.rabbit.RabbitConnection;
import com.grookage.qtrouper.utils.SerDe;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author koushik
 */
@NoArgsConstructor
public abstract class TrouperBundle<T extends Configuration> implements ConfiguredBundle<T> {

    public abstract RabbitConfiguration getRabbitConfiguration(T configuration);
    @Getter
    private RabbitConnection rabbitConnection;

    /**
     * Sets the objectMapper properties and initializes RabbitConnection, along with its health check
     * @param configuration     {@link T}               The typed configuration against which the said TrouperBundle is initialized
     * @param environment       {@link Environment}     The dropwizard environment object.
     */
    @Override
    public void run(T configuration, Environment environment){
        environment.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        environment.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        environment.getObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        environment.getObjectMapper().configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        environment.getObjectMapper().configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
        SerDe.init(environment.getObjectMapper());
        rabbitConnection = new RabbitConnection(getRabbitConfiguration(configuration), environment.metrics());
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() {
                rabbitConnection.start();
            }

            @Override
            public void stop() {
                rabbitConnection.stop();
            }
        });
        environment.healthChecks().register("qTrouper-health-check", new TrouperHealthCheck(rabbitConnection));
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }
}


