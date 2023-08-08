package com.grookage.qtrouper.core.models;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grookage.qtrouper.utils.SerDe;
import org.junit.Assert;
import org.junit.Test;

public class QueueContextTest {

    @Test
    public void testQueueContext(){
        SerDe.init(new ObjectMapper());
        final var queueContext = QueueContext.builder().build();
        Assert.assertEquals(0, queueContext.getMessagePriority());
        queueContext.addContext("test", "test");
        final var returnValue = queueContext.getContext("test", String.class);
        Assert.assertNotNull(returnValue);
        Assert.assertEquals("test", returnValue);
        queueContext.setMessagePriority(10);
        Assert.assertEquals(10, queueContext.getMessagePriority());
        queueContext.resetMessagePriority();
        Assert.assertEquals(0, queueContext.getMessagePriority());
    }
}
