/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.PayloadTransformer;

public class NimbusPayloadTransformer implements PayloadTransformer<JsonNode> {

    @Override
    public JsonNode transform(Payload payload) {
        return JSONUtil.asJson(payload.toJSONObject());
    }
}
