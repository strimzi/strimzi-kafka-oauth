/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import static com.jayway.jsonpath.JsonPath.using;

/**
 * This class implements the support for JSONPath querying as implemented by:
 *
 *    https://github.com/json-path/JsonPath
 *
 * Given the following content of the JWT token:
 * <pre>
 *   {
 *     "aud": ["uma_authorization", "kafka"],
 *     "iss": "https://auth-server/sso",
 *     "iat": 0,
 *     "exp": 600,
 *     "sub": "username",
 *     "custom": "custom-value",
 *     "roles": {
 *       "client-roles": {
 *         "kafka": ["kafka-user"]
 *       }
 *     },
 *     "custom-level": 9
 *   }
 * </pre>
 *
 * Some examples of valid queries are:
 *
 * <pre>
 *   {@literal $}.custom
 *   {@literal $}['aud']
 *   {@literal $}.roles.client-roles.kafka
 *   {@literal $}['roles']['client-roles']['kafka']
 * </pre>
 *
 * See <a href="https://github.com/json-path/JsonPath">Jayway JsonPath project</a> for full syntax.
 * <p>
 * This class takes a JSONPath expression and applies it as-is.
 * <p>
 * The JWT token is used in its original payload form, for example:
 * <pre>
 *   {
 *      "sub": "username",
 *      "iss": "https://auth-server/sso",
 *      "custom": "custom value",
 *      ...
 *   }
 * </pre>
 *
 * Usage:
 * <pre>
 *   JsonPathQuery query = new JsonPathQuery("$.roles");
 *   JsonNode result = query.apply(jsonObject);
 * </pre>
 *
 * Query is parsed in the first line and any error during parsing results
 * in {@link JsonPathQueryException}.
 *
 * Matching is thread safe. The normal usage pattern is to initialise the JsonPathFilterQuery object once,
 * and query it many times concurrently against json objects.
 *
 * In addition to filtering this helper can also be used to apply JsonPath query to extract a result containing the matching keys.
 *
 */
public class JsonPathQuery {

    private final Matcher matcher;

    private JsonPathQuery(String query) {
        Configuration conf = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .options(Option.SUPPRESS_EXCEPTIONS)
                .build();

        ParseContext ctx = using(conf);
        try {
            this.matcher = new Matcher(ctx, query, false);
        } catch (JsonPathException e) {
            throw new JsonPathQueryException("Failed to parse filter query: \"" + query + "\"", e);
        }
    }

    /**
     * Construct a new JsonPathQuery
     *
     * @param query The query using the JSONPath syntax
     * @return New JsonPathQuery instance
     */
    public static JsonPathQuery parse(String query) {
        return new JsonPathQuery(query);
    }

    /**
     * Apply the JsonPath query to passed object
     *
     * @param jsonObject Jackson DataBind object
     * @return The Jackson JsonNode object with the result
     */
    public JsonNode apply(JsonNode jsonObject) {
        return matcher.apply(jsonObject);
    }

    @Override
    public String toString() {
        return matcher.toString();
    }
}
