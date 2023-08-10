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
package com.grookage.qtrouper.core.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grookage.qtrouper.utils.SerDe;
import org.junit.Assert;
import org.junit.Test;

public class QueueContextTest {

    @Test
    public void testQueueContext() {
        SerDe.init(new ObjectMapper());
        final var queueContext = QueueContext.builder()
                .build();
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
