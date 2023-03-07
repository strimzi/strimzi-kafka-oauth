/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.PayloadTransformer;

/**
 * An interface to convert from Nimbus Jose JWT <code>Payload</code> object to Jackson Databind <code>JsonNode</code> object
 */
public class NimbusPayloadTransformer implements PayloadTransformer<JsonNode> {

    /**
     * Convert a <code>Payload</code> object to <code>JsonNode</code>
     *
     * @param payload The payload object
     * @return The <code>JsonNode</code>
     */
    @Override
    public JsonNode transform(Payload payload) {
        return JSONUtil.asJson(payload.toJSONObject());
    }
}
