/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;

class JsonKeyValue {
    String key;
    JsonNode value;

    JsonKeyValue(String key, JsonNode value) {
        this.key = key;
        this.value = value;
    }
}
