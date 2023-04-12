/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Helper methods to work with JSON
 */
public class JSONUtil {

    /**
     * A Jackson Databind <code>ObjectMapper</code> singleton
     */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Create a new ObjectNode
     *
     * @return A new ObjectNode
     */
    public static ObjectNode newObjectNode() {
        return new ObjectNode(MAPPER.getNodeFactory());
    }

    /**
     * Parse JSON from <code>InputStream</code> into a specified type
     *
     * @param is An <code>InputStream</code> to read JSON from
     * @param clazz A class representing the type to return (e.g. <code>JsonNode</code>, <code>ObjectNode</code>, <code>ArrayNode</code>, <code>String</code>)
     * @return Parsed JSON as an object
     * @param <T> Generic type representing a return type
     * @throws IOException If an error occurs while reading or parsing the input
     */
    public static <T> T readJSON(InputStream is, Class<T> clazz) throws IOException {
        if (clazz == String.class) {
            // just read and convert to UTF-8 String
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtil.copy(is, baos);
            return clazz.cast(new String(baos.toByteArray(), StandardCharsets.UTF_8));
        }
        return MAPPER.readValue(is, clazz);
    }

    /**
     * Parse JSON from String into a specified type
     *
     * @param jsonString JSON as a String
     * @param clazz A class representing the type to return (e.g. <code>JsonNode</code>, <code>ObjectNode</code>, <code>ArrayNode</code>, <code>String</code>)
     * @return Parsed JSON as an object
     * @param <T> Generic type representing a return type
     * @throws IOException If an error occurs while reading or parsing the input
     */
    public static <T> T readJSON(String jsonString, Class<T> clazz) throws IOException {
        return MAPPER.readValue(jsonString, clazz);
    }

    /**
     * Convert object to <code>JsonNode</code>
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
            throw new IllegalArgumentException("Failed to convert value to JSON (" + value + ")", e);
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

    /**
     * This method takes a JsonNode representing an array, or a string, and converts it into a List of String items.
     * <p>
     * If the passed node is a TextNode, the text is parsed into a list of items by using ' ' (space) as a delimiter.
     * The resulting list can contain empty strings if two delimiters are present next to one another.
     * <p>
     * If the JsonNode is neither an ArrayNode, nor a TextNode an IllegalArgumentException is thrown.
     *
     * @param arrayOrString A JsonNode to convert into a list of String
     * @return A list of String
     */
    public static List<String> asListOfString(JsonNode arrayOrString) {
        return asListOfString(arrayOrString, " ");
    }

    /**
     * This method takes a JsonNode representing an array, or a string, and converts it into a List of String items.
     * <p>
     * The {@code delimiter} parameter is only used if the passed node is a TextNode. It is used to parse the node content
     * as a list of strings. The resulting list can contain empty strings if two delimiters are present next to one another.
     * <p>
     * If the JsonNode is neither an ArrayNode, nor a TextNode an IllegalArgumentException is thrown.
     *
     * @param arrayOrString A JsonNode to convert into a list of String
     * @param delimiter A delimiter to use for parsing the TextNode
     * @return A list of String
     */
    public static List<String> asListOfString(JsonNode arrayOrString, String delimiter) {

        ArrayList<String> result = new ArrayList<>();

        if (arrayOrString.isTextual()) {
            result.addAll(Arrays.stream(arrayOrString.asText().split(Pattern.quote(delimiter))).map(String::trim).collect(Collectors.toList()));
        } else {
            if (!arrayOrString.isArray()) {
                throw new IllegalArgumentException("JsonNode not a text node, nor an array node: " + arrayOrString);
            }

            for (JsonNode n : arrayOrString) {
                if (n.isTextual()) {
                    result.add(n.asText());
                } else {
                    result.add(n.toString());
                }
            }
        }

        return result;
    }

    /**
     * Set an array attribute on a JSON object to a collection of Strings
     *
     * @param object The target JSON object
     * @param attrName An attribute name
     * @param elements The collection of strings
     * @return Newly created ArrayNode
     */
    public static ArrayNode setArrayOfStringsIfNotNull(JsonNode object, String attrName, Collection<String> elements) {
        if (elements == null) {
            return null;
        }
        if (!(object instanceof ObjectNode)) {
            throw new IllegalArgumentException("Unexpected JSON Node type (not ObjectNode): " + object.getClass());
        }

        ArrayNode list = ((ObjectNode) object).putArray(attrName);
        for (String g: elements) {
            list.add(g);
        }
        return list;
    }
}
