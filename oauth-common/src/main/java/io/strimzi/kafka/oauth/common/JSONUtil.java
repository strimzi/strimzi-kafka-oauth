/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JSONUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode newObjectNode() {
        return new ObjectNode(MAPPER.getNodeFactory());
    }

    public static <T> T readJSON(InputStream is, Class<T> clazz) throws IOException {
        if (clazz == String.class) {
            // just read and convert to UTF-8 String
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtil.copy(is, baos);
            return clazz.cast(new String(baos.toByteArray(), StandardCharsets.UTF_8));
        }
        return MAPPER.readValue(is, clazz);
    }

    public static <T> T readJSON(String jsonString, Class<T> clazz) throws IOException {
        return MAPPER.readValue(jsonString, clazz);
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

        // Convert efficiently into generic json object
        try {
            return MAPPER.convertValue(value, JsonNode.class);
        } catch (RuntimeException e) {
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

    public static List<String> asListOfString(JsonNode arrayOrString) {
        return asListOfString(arrayOrString, " ");
    }

    public static List<String> asListOfString(JsonNode arrayOrString, String delimiter) {

        ArrayList<String> result = new ArrayList<>();

        if (arrayOrString.isTextual()) {
            result.addAll(Arrays.asList(arrayOrString.asText().split(Pattern.quote(delimiter)))
                    .stream().map(String::trim).collect(Collectors.toList()));
        } else {
            if (!arrayOrString.isArray()) {
                throw new IllegalArgumentException("JsonNode not a text node, nor an array node: " + arrayOrString);
            }

            Iterator<JsonNode> it = arrayOrString.iterator();
            while (it.hasNext()) {
                JsonNode n = it.next();
                if (n.isTextual()) {
                    result.add(n.asText());
                } else {
                    result.add(n.toString());
                }
            }
        }

        return result;
    }
}
