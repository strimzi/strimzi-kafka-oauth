/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JSONUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static <T> T readJSON(InputStream is, Class<T> clazz) throws IOException {
        return MAPPER.readValue(is, clazz);
    }

    /**
     * Convert object to JsonNode
     *
     * @param value Json-serializable object
     * @return Object as JsonNode
     */
    public static JsonNode asJson(Object value) {
        if (value instanceof JsonNode)
            return (JsonNode) value;

        // We re-serialise and deserialize into generic json object
        try {
            String jsonString = JsonSerialization.writeValueAsString(value);
            return JsonSerialization.readValue(jsonString, JsonNode.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert value to JSON (" + value + ")", e);
        }
    }

    /**
     * Get specific claim from token.
     *
     * @param claim jq style query where nested names are specified using '.' as separator
     * @param token parsed object
     * @return Value of the specific claim as String or null if claim not present
     */
    public static String getClaimFromJWT(String claim, Object token) {
        // No nice way to get arbitrary claim from already parsed token
        JsonNode node = asJson(token);
        return getClaimFromJWT(node, claim.split("\\."));
    }

    /**
     * Get specific claim from token.
     *
     * @param node parsed JWT token payload
     * @param path name segments where all but last should each point to the next nested object
     * @return Value of the specific claim as String or null if claim not present
     */
    public static String getClaimFromJWT(JsonNode node, String... path) {
        for (String p: path) {
            node = node.get(p);
            if (node == null) {
                return null;
            }
        }
        return node.asText();
    }

    public static List<String> asListOfString(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            throw new IllegalArgumentException("JsonNode not an array node: " + arrayNode);
        }
        ArrayList<String> result = new ArrayList<>();
        Iterator<JsonNode> it = arrayNode.iterator();
        while (it.hasNext()) {
            result.add(it.next().asText());
        }
        return result;
    }
}
