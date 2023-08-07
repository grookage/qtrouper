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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.grookage.qtrouper.core.exceptions.TrouperExceptions;
import com.grookage.qtrouper.utils.SerDe;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @author koushik
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@SuppressWarnings("unused")
public class QueueContext implements Serializable {

    private String serviceReference;
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
    @Builder.Default
    private int messagePriority = 0;

    public <T> void addContext(Class<T> klass,
                               T value) {
        addContext(klass.getSimpleName()
                .toUpperCase(), value);
    }

    @JsonIgnore
    public <T> T getContext(Class<T> tClass) {
        return getContext(tClass.getSimpleName()
                .toUpperCase(), tClass);
    }

    public <T> void addContext(String key,
                               T value) {
        if (Strings.isNullOrEmpty(key.toUpperCase())) {
            TrouperExceptions.illegalArgument("Invalid key for context data. Key cannot be null/empty");
        }
        this.data.put(key.toUpperCase(), value);
    }

    @JsonIgnore
    public <T> T getContext(String key,
                            Class<T> tClass) {
        final var value = this.data.get(key.toUpperCase());
        return null == value
               ? null
               : SerDe.mapper()
                       .convertValue(value, tClass);
    }

    public void resetMessagePriority() {
        this.setMessagePriority(0);
    }
}
