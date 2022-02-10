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

import lombok.val;
import org.junit.Assert;
import org.junit.Test;

public class QueueConfigurationTest {

  @Test
  public void testQueueConfigurationDefaultViaConstructor() {
      final var queueConfiguration = new QueueConfiguration();
      Assert.assertFalse(queueConfiguration.isConsumerDisabled());
      Assert.assertEquals(3, queueConfiguration.getConcurrency());
      Assert.assertEquals("qtrouper", queueConfiguration.getNamespace());
  }

  @Test
  public void testQueueConfigurationDefaultViaBuilder() {
      final var queueViaBuilder = QueueConfiguration.builder().build();
      Assert.assertFalse(queueViaBuilder.isConsumerDisabled());
      Assert.assertEquals(3, queueViaBuilder.getConcurrency());
      Assert.assertEquals("qtrouper", queueViaBuilder.getNamespace());
  }
}