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
package com.grookage.qtrouper.core.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.grookage.qtrouper.Trouper;
import com.grookage.qtrouper.Trouper.Handler;
import com.grookage.qtrouper.core.models.QAccessInfo;
import com.grookage.qtrouper.core.models.QueueContext;
import com.grookage.qtrouper.core.rabbit.RabbitConfiguration;
import com.grookage.qtrouper.core.rabbit.RabbitConnection;
import com.grookage.qtrouper.utils.SerDe;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
public class HandlerTest {

    private final String mainQueueHandlerTag = "MAIN_QUEUE_HANDLER_TAG";
    private final String sidelineQueueHandlerTag = "SIDELINE_QUEUE_HANDLER_TAG";
    private final Long deliveryTag = 73656L;
    private Channel channel;
    private RabbitConnection rabbitConnection;
    private Envelope envelope;
    private ArgumentCaptor<Long> deliveryTagCaptor;
    private ArgumentCaptor<Boolean> boolCaptor;

    @Before
    public void setUp() {
        channel = mock(Channel.class);
        rabbitConnection = mock(RabbitConnection.class);
        envelope = mock(Envelope.class);
        deliveryTagCaptor = ArgumentCaptor.forClass(Long.class);
        boolCaptor = ArgumentCaptor.forClass(Boolean.class);

        SerDe.init(new ObjectMapper());

        setupBaseMocks();
    }

    private void setupBaseMocks() {
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
        when(rabbitConnection.getConnection()).thenReturn(mock(Connection.class));
        when(rabbitConnection.channel()).thenReturn(channel);
        when(envelope.getDeliveryTag()).thenReturn(deliveryTag);
    }

    private Handler getHandler(String queueTag,
                               ExceptionInterface testInterface,
                               boolean retryEnabled,
                               int maxRetries) throws IOException {
        final var queueConfiguration = QueueConfiguration.builder()
                .retry(RetryConfiguration.builder()
                        .enabled(retryEnabled)
                        .maxRetries(maxRetries)
                        .build())
                .sideline(SidelineConfiguration.builder()
                        .enabled(true)
                        .concurrency(1)
                        .build())
                .queueName("TEST_QUEUE_1")
                .concurrency(1)
                .build();
        when(channel.basicConsume(anyString(), anyBoolean(), any())).thenReturn(mainQueueHandlerTag)
                .thenReturn(sidelineQueueHandlerTag);

        // Create a trouper
        final var testTrouper = new ExceptionTrouper(queueConfiguration.getQueueName(), queueConfiguration,
                rabbitConnection, QueueContext.class, Sets.newHashSet(KnowException.class), testInterface);
        testTrouper.start();

        // Get the handler
        return testTrouper.getHandlers()
                .stream()
                .filter(handler -> handler.getTag()
                        .equalsIgnoreCase(queueTag))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find handler"));
    }

    private Handler getHandlerAfterSettingUp(String queueTag,
                                             ExceptionInterface testInterface) throws IOException {
        return getHandler(queueTag, testInterface, false, 0);
    }

    @Test
    public void testBasicAckAndSidelineWhenTrouperProcessThrowUnknownException() throws IOException {

        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(mainQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.process()).thenThrow(new RuntimeException("test exp"));

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicAck(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(false, boolCaptor.getValue());
        verify(channel, times(1)).basicPublish(any(), any(), any(), any());
    }

    @Test
    public void testBasicAckWhenTrouperSidelineIsSuccess() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(sidelineQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.processSideline()).thenReturn(true);

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicAck(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(false, boolCaptor.getValue());
    }

    @Test
    public void testBasicRejectWhenTrouperSidelineIsFailure() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(sidelineQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.processSideline()).thenReturn(false);

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicReject(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(true, boolCaptor.getValue());
    }

    @Test
    public void testBasicAckWhenTrouperSidelineThrowsKnowException() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(sidelineQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.processSideline()).thenThrow(new KnowException());

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicAck(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(false, boolCaptor.getValue());
    }

    @Test
    public void testBasicAckWhenTrouperSidelineThrowsUnKnowException() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(sidelineQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.processSideline()).thenThrow(new RuntimeException());

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicReject(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(true, boolCaptor.getValue());
    }

    @Test
    public void testBasicAckWhenTrouperProcessIsSuccess() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(mainQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.process()).thenReturn(true);

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicAck(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(false, boolCaptor.getValue());
    }

    @Test
    public void testBasicAckAndSidelineWhenTrouperProcessIsFailure() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(mainQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.process()).thenReturn(false);

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicAck(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(false, boolCaptor.getValue());
        verify(channel, times(1)).basicPublish(any(), any(), any(), any());
    }

    @Test
    public void testBasicAckWhenTrouperProcessThrowsKnowException() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandlerAfterSettingUp(mainQueueHandlerTag, exceptionInterface);

        when(exceptionInterface.process()).thenThrow(new KnowException());

        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .build()));

        verify(channel).basicAck(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(false, boolCaptor.getValue());
    }

    @Test
    public void testPriorityQueueConsumptionOnRetryWithInsufficientMaxRetries() throws IOException {
        final var exceptionInterface = mock(ExceptionInterface.class);
        final var testHandler = getHandler(mainQueueHandlerTag, exceptionInterface, true, 0);
        when(exceptionInterface.process()).thenThrow(new KnowException());
        testHandler.handleDelivery("ANY", envelope, null, SerDe.mapper()
                .writeValueAsBytes(QueueContext.builder()
                        .messagePriority(1)
                        .build()));
        verify(channel).basicAck(deliveryTagCaptor.capture(), boolCaptor.capture());
        Assert.assertEquals(deliveryTag, deliveryTagCaptor.getValue());
        Assert.assertEquals(false, boolCaptor.getValue());
        verify(channel, times(1)).basicPublish(any(), any(), any(), any());
    }

    interface ExceptionInterface {

        boolean process();

        boolean processSideline();
    }

    static class ExceptionTrouper extends Trouper<QueueContext> {

        private final ExceptionInterface testInterface;

        protected ExceptionTrouper(String queueName,
                                   QueueConfiguration config,
                                   RabbitConnection connection,
                                   Class<? extends QueueContext> clazz,
                                   Set<Class<?>> droppedExceptionTypes,
                                   ExceptionInterface testInterface) {
            super(queueName, config, connection, clazz, droppedExceptionTypes);
            this.testInterface = testInterface;
        }

        @Override
        public boolean process(QueueContext queueContext,
                               QAccessInfo accessInfo) {
            return testInterface.process();
        }

        @Override
        public boolean processSideline(QueueContext queueContext,
                                       QAccessInfo accessInfo) {
            return testInterface.processSideline();
        }
    }

    static class KnowException extends RuntimeException {

    }

}
