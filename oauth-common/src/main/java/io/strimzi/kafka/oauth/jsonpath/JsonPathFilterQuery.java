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
 * This class implements the support for JSONPath filter querying as implemented by:
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
 *   {@literal @}.custom == 'custom-value'
 *   {@literal @}.sub &amp;&amp; {@literal @}.custom == 'custom-value'
 *   {@literal @}.custom == 'custom-value' || 'kafka' in {@literal @}.aud
 *   {@literal @}.custom == 'custom-value' &amp;&amp; {@literal @}.roles.client-roles.kafka
 *   {@literal @}.['custom'] == 'custom-value'
 *   {@literal @}.custom in ['custom-value','custom-value2','custom-value3']
 *   {@literal @}.custom == 'custom-value' || {@literal @}.custom == 'custom-value2' || {@literal @}.custom == 'custom-value2'
 *   {@literal @}.custom-level &amp;&amp; {@literal @}.custom-level nin [1,2,3]
 *   "kafka-user" in {@literal @}.['roles'].['client-roles'].['kafka']
 *   {@literal @}.roles.client-roles.kafka &amp;&amp; "kafka-admin" nin {@literal @}.roles.client-roles.kafka
 *   {@literal @}.custom =~ /^CUSTOM-.+$/i
 *   {@literal @}.custom == 'custom-value' &amp;&amp; ('kafka' in {@literal @}.aud || 'kafka-user' in {@literal @}.roles.client-roles.kafka)
 *   {@literal @}.custom =~ /^custom-.+/
 *   {@literal @}.iss =~ /^https:\/\/auth-server\/.+/
 *   !{@literal @}.internal_id
 * </pre>
 *
 * See <a href="https://github.com/json-path/JsonPath">Jayway JsonPath project</a> for full syntax.
 * <p>
 * This class takes a JSONPath filter expression and rewrites it so it can be applied to the parsed JWT token as a filtering selector.
 * <p>
 * The query is rewritten into JSONPath as:
 * <pre>
 *   $[*][?(QUERY)]
 * </pre>
 * For example: '$[*][?(@.custom == 'custom value')]'
 *
 * The JWT token is wrapped into another JSON object as follows:
 * <pre>
 *   {
 *       "token": {
 *         "sub": "username",
 *         "iss": "https://auth-server/sso",
 *         "custom": "custom value",
 *         ...
 *       }
 *   }
 * </pre>
 *
 * Usage:
 * <pre>
 *   JsonPathFilterQuery query = new JsonPathFilterQuery("@.custom == 'value'");
 *   boolean match = query.matches(jsonObject);
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
public class JsonPathFilterQuery {

    private final Matcher matcher;

    private JsonPathFilterQuery(String query) {
        Configuration conf = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .options(Option.SUPPRESS_EXCEPTIONS)
                .build();

        ParseContext ctx = using(conf);
        try {
            this.matcher = new Matcher(ctx, query);
        } catch (JsonPathException e) {
            throw new JsonPathQueryException("Failed to parse filter query: \"" + query + "\"", e);
        }
    }

    /**
     * Construct a new JsonPathFilterQuery
     *
     * @param query The query using the JSONPath filter syntax
     * @return New JsonPathFilerQuery instance
     */
    public static JsonPathFilterQuery parse(String query) {
        return new JsonPathFilterQuery(query);
    }

    /**
     * Match the json objects against the filter query.
     *
     * @param jsonObject Jackson DataBind object
     * @return true if the object matches the filter, false otherwise
     */
    public boolean matches(JsonNode jsonObject) {
        return matcher.matches(jsonObject);
    }


    @Override
    public String toString() {
        return matcher.toString();
    }

}
