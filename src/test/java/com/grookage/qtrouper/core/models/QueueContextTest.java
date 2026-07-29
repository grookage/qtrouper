package com.grookage.qtrouper.core.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grookage.qtrouper.utils.SerDe;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class QueueContextTest {

    @Test
    public void testQueueContext(){
        SerDe.init(new ObjectMapper());
        final var queueContext = QueueContext.builder().build();
        Assertions.assertEquals(0, queueContext.getMessagePriority());
        queueContext.addContext("test", "test");
        final var returnValue = queueContext.getContext("test", String.class);
        Assertions.assertNotNull(returnValue);
        Assertions.assertEquals("test", returnValue);
        queueContext.setMessagePriority(10);
        Assertions.assertEquals(10, queueContext.getMessagePriority());
        queueContext.resetMessagePriority();
        Assertions.assertEquals(0, queueContext.getMessagePriority());
    }
}
