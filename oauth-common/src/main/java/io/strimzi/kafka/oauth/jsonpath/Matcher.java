/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;

import static io.strimzi.kafka.oauth.common.JSONUtil.MAPPER;

/**
 * <em>Matcher</em> is used for matching the JSON object against the parsed JSONPath filter query.
 *
 * This class is thread-safe, and can be used by multiple threads at the same time.
 *
 * Initialise the <em>Matcher</em> with the result of the {@link JsonPathFilterQuery#parse(String)} method.
 * Store the reference, and use it concurrently by calling the {@link Matcher#matches(JsonNode)} method,
 * passing it the JSON object to match against the parsed filter.
 */
class Matcher {

    private final ParseContext ctx;
    private final JsonPath parsed;
    private final String query;

    Matcher(ParseContext ctx, String query) {
        this(ctx, query, true);
    }

    Matcher(ParseContext ctx, String query, boolean rewriteQuery) {
        this.ctx = ctx;
        this.query = query;
        this.parsed = JsonPath.compile(rewriteQuery ? "$[*][?(" + query + ")]" : query);
    }

    /**
     * Match the JSON object against the JSONPath filter query as described in {@link JsonPathFilterQuery}.
     * The passed JsonObject is first wrapped as:
     *    {
     *        "token": JSON
     *    }
     * @param json Jackson JsonObject to match
     * @return true if the object matches the filter, false otherwise
     */
    public boolean matches(JsonNode json) {
        json = wrapToken(json);
        DocumentContext doc = ctx.parse(json);
        ArrayNode result = doc.read(parsed);

        return result.size() == 1;
    }

    /**
     * Apply the JSONPath query to the passed JSON object.
     * The passed JsonObject is not wrapped.
     *
     * @param json Jackson JsonObject to extract from
     * @return Jackson JsonObject with the result
     */
    public JsonNode apply(JsonNode json) {
        DocumentContext doc = ctx.parse(json);
        return doc.read(parsed);
    }

    private JsonNode wrapToken(JsonNode json) {
        JsonNodeFactory nodeFactory = MAPPER.getNodeFactory();
        return nodeFactory.objectNode().set("token", json);
    }

    @Override
    public String toString() {
        return query;
    }
}
