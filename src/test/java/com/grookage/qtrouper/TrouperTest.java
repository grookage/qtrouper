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


import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grookage.qtrouper.core.config.QueueConfiguration;
import com.grookage.qtrouper.core.config.RetryConfiguration;
import com.grookage.qtrouper.core.config.SidelineConfiguration;
import com.grookage.qtrouper.core.models.QAccessInfo;
import com.grookage.qtrouper.core.models.QueueContext;
import com.grookage.qtrouper.core.rabbit.RabbitConfiguration;
import com.grookage.qtrouper.core.rabbit.RabbitConnection;
import com.grookage.qtrouper.utils.SerDe;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author koushik
 */
@SuppressWarnings({"unchecked", "unused"})
public class TrouperTest {

    private static final String DEFAULT_NAMESPACE = "default";
    private final Connection connection = mock(Connection.class);
    private final Channel channel = mock(Channel.class);
    private RabbitConnection rabbitConnection;

    private RetryConfiguration getRetryConfiguration() {
        return RetryConfiguration.builder()
                .enabled(false)
                .maxRetries(10)
                .ttlMs(100)
                .build();
    }

    private SidelineConfiguration getSidelineConfiguration(boolean enabled,
                                                           int concurrency) {
        return SidelineConfiguration.builder()
                .enabled(enabled)
                .concurrency(concurrency)
                .build();
    }

    @Before
    public void setup() {
        this.rabbitConnection = mock(RabbitConnection.class);
    }

    private TestTrouper getTrouperAfterStart(QueueConfiguration queueConfiguration) throws Exception {
        final var rmqOpts = mock(Map.class);
        final var rabbitConfiguration = RabbitConfiguration.builder()
                .brokers(new ArrayList<>())
                .password("")
                .userName("")
                .virtualHost("/")
                .threadPoolSize(100)
                .build();
        when(rabbitConnection.getConfig()).thenReturn(rabbitConfiguration);
        when(rabbitConnection.rmqOpts()).thenReturn(rmqOpts);
        when(rabbitConnection.newChannel()).thenReturn(channel);
        when(rabbitConnection.getConnection()).thenReturn(connection);
        when(rabbitConnection.channel()).thenReturn(channel);
        final var testTrouper = new TestTrouper(queueConfiguration.getQueueName(), queueConfiguration, rabbitConnection,
                QueueContext.class, new HashSet<>());
        testTrouper.start();
        verify(channel, times(2)).exchangeDeclare(anyString(), anyString(), anyBoolean(), anyBoolean(), any());
        verify(rabbitConnection, times(3)).ensure(anyString(), anyString(), any(Map.class));
        return testTrouper;
    }

    @Test
    public void trouperStartTestWithNoConsumers() throws Exception {
        final var queueConfiguration = QueueConfiguration.builder()
                .queueName("queue")
                .namespace(DEFAULT_NAMESPACE)
                .concurrency(0)
                .prefetchCount(10)
                .consumerDisabled(true)
                .build();
        final var trouper = getTrouperAfterStart(queueConfiguration);
        trouper.stop();
    }

    @Test
    public void trouperStartTestWithNoRetryAndSideline() throws Exception {
        final var queueConfiguration = QueueConfiguration.builder()
                .queueName("queue")
                .namespace(DEFAULT_NAMESPACE)
                .concurrency(0)
                .prefetchCount(10)
                .consumerDisabled(false)
                .retry(getRetryConfiguration())
                .sideline(getSidelineConfiguration(false, 0))
                .build();
        final var trouper = getTrouperAfterStart(queueConfiguration);
        trouper.stop();
    }

    @Test
    public void trouperStartTestWithOnlySideline() throws Exception {
        final var queueConfiguration = QueueConfiguration.builder()
                .queueName("queue")
                .namespace(DEFAULT_NAMESPACE)
                .concurrency(0)
                .prefetchCount(10)
                .consumerDisabled(false)
                .retry(getRetryConfiguration())
                .sideline(getSidelineConfiguration(true, 1))
                .build();
        when(channel.basicConsume(anyString(), anyBoolean(), any())).thenReturn("tag");
        final var trouper = getTrouperAfterStart(queueConfiguration);
        Assert.assertEquals(1, trouper.getHandlers()
                .size());
        trouper.stop();
    }

    @Test
    public void trouperStartWithAllEncompassingConfig() throws Exception {
        final var queueConfiguration = QueueConfiguration.builder()
                .queueName("queue")
                .namespace(DEFAULT_NAMESPACE)
                .concurrency(10)
                .prefetchCount(10)
                .consumerDisabled(false)
                .retry(getRetryConfiguration())
                .sideline(getSidelineConfiguration(true, 10))
                .build();
        final var trouper = getTrouperAfterStart(queueConfiguration);
        Assert.assertEquals(20, trouper.getHandlers()
                .size());
        trouper.stop();
    }

    static class TestTrouper extends Trouper<QueueContext> {

        protected TestTrouper(String queueName,
                              QueueConfiguration config,
                              RabbitConnection connection,
                              Class<? extends QueueContext> clazz,
                              Set<Class<?>> droppedExceptionTypes) {
            super(queueName, config, connection, clazz, droppedExceptionTypes);
        }

        @Override
        public boolean process(QueueContext queueContext,
                               QAccessInfo accessInfo) {
            return true;
        }

        @Override
        public boolean processSideline(QueueContext queueContext,
                                       QAccessInfo accessInfo) {
            return true;
        }
    }
}
