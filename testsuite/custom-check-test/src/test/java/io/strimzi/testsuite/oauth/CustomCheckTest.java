/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import io.strimzi.kafka.oauth.jsonpath.JsonPathFilterQuery;
import io.strimzi.kafka.oauth.jsonpath.JsonPathFilterQueryException;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.util.JsonSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.Locale;
import java.util.Set;

import static com.jayway.jsonpath.JsonPath.using;
import static io.strimzi.kafka.oauth.common.JSONUtil.MAPPER;

public class CustomCheckTest {

    final static Logger log = LoggerFactory.getLogger(CustomCheckTest.class);

    final String jsonString = "{ " +
        "\"aud\": [\"uma_authorization\", \"kafka\"], " +
        "\"iss\": \"https://auth-server/token\", " +
        "\"iat\": 0, " +
        "\"exp\": 600, " +
        "\"sub\": \"username\", " +
        "\"custom\": \"custom-value\"," +
        "\"roles\": {\"client-roles\": {\"kafka\": [\"kafka-user\"]} }, " +
        "\"custom-level\": 9 " +
        "}";

    final String[] queries = {
        "@.custom-level != -1", "true",
        "@.exp < 1000", "true",
        "@.exp > 1000", "false",
        "@.exp >= 600", "true",
        "@.exp <= 600", "true",
        "@.exp > 600", "false",
        "@.exp < 600", "false",
        "@.exp < 'cant compare makes it false'", "false",
        "@.custom < 'custom-value2'", "true",
        "@.custom > 'custom-val'", "true",
        "@.custom > 'aaaaaaaaaaaaaaaa'", "true",
        "@.custom > 'AAAAAAAAAAAAAAAA'", "true",
        "@.custom > 'ZZZZZZZZZZZZZZZZ'", "true",
        "@.custom > 'z'", "false",
        "@.custom == 'custom-value'", "true",
        "@.custom == 'custom-value' && @.exp > 1000", "false",
        "@.custom == 'custom-value' || @.exp > 1000", "true",
        "@.custom == 'custom-value' && @.exp <= 1000", "true",
        "@.custom != 'custom-value'", "false",
        "@.iat != null", "true",
        "@.iat == null", "false",
        "@.custom in ['some-custom-value', 42, 'custom-value']", "true",
        "@.custom nin ['some-custom-value', 42, 'custom-value']", "false",
        "@.custom nin []", "true",
        "@.custom in []", "false",
        "@.custom-level in [1,2,3]", "false",
        "@.custom-level in [1,8,9,20]", "true",
        "@.custom-level nin [1,8,9,20]", "false",
        "@.roles.client-roles.kafka != null", "true",
        "'kafka' in @.aud", "true",
        "\"kafka-user\" in @.roles.client-roles.kafka", "true",
        "@.exp > 1000 || 'kafka' in @.aud", "true",
        "(@.custom == 'custom-value' || @.custom == 'custom-value2')", "true",
        "(@.exp > 1000 && @.custom == 'custom-value') || @.roles.client-roles.kafka != null", "true",
        "@.exp >= 600 && ('kafka' in @.aud || @.custom == 'custom-value')", "true",
        "@.roles.client-roles.kafka anyof ['kafka-admin', 'kafka-user']", "true",
        "@.roles.client-roles.kafka noneof ['kafka-admin', 'kafka-user']", "false",
        "@.sub anyof ['username']", "false",
        "@.missing anyof [null, 'username']", "false",
        "@.missing noneof [null, 'username']", "false",
        "@.aud noneof [null, 'username']", "true",
        "@.aud noneof ['kafka', 'something']", "false",
        "@.aud anyof ['kafka', 'something']", "true",
        "!(@.aud anyof ['kafka', 'something'])", "false",
        "!(@.aud noneof [null, 'username'])", "false",
        "((@.custom == 'some-custom-value' || 'kafka' in @.aud) && @.exp > 1000)", "false",
        "((('kafka' in @.aud || @.custom == 'custom-value') && @.exp > 1000))", "false",
        "@.exp =~ /^6[0-9][0-9]$/", "true",
        "@.custom =~ /custom-.+/", "true",
        "@.custom =~ /ustom-.+/", "false",
        "@.custom =~ /CUSTOM-.+/i", "true",
        "@.iss =~ /https:\\/\\/auth-server\\/.+/", "true",
        "@.iss =~ /https:\\/\\/auth-server\\//", "false",
        "@.custom == 'custom-value' && @.exp > 1000", "false",
        "@.custom == 'custom-value' || @.exp > 1000", "true",
        "(@.custom == 'custom-value' || @.custom == 'custom-value2')", "true",
        "@.missing == null", "false",
        "@.missing != null", "true",
        "@.missing", "false",
        "!@.missing", "true",
        "@.custom", "true"
    };

    /*
     * These are the queries supported by our implementation but not by Jayway implementation.
     * It's the use of 'and' and 'or', and also the support of option switches within regex.
     */
    final String[] nonStandardQueries = {
        "(@.exp > 1000 && @.custom == 'custom-value') or @.roles.client-roles.kafka != null", "true",
        "@.exp >= 600 and ('kafka' in @.aud || @.custom == 'custom-value')", "true",
        "((@.custom == 'some-custom-value' || 'kafka' in @.aud) and @.exp > 1000)", "false",
        "((('kafka' in @.aud || @.custom == 'custom-value') and @.exp > 1000))", "false",
        "@.custom == 'custom-value' and @.exp > 1000", "false",
        "@.custom == 'custom-value' or @.exp > 1000", "true",
        "(@.custom == 'custom-value' or @.custom == 'custom-value2')", "true",
        "@.custom =~ /(?i)CUSTOM-.+/", "true",
    };

    /*
     * These are queries supported by Jayway implementation but not supported by our implementation.
     * It's mostly the lack of whitespace.
     */
    final String[] unsupportedStandardQueries = {
        "@.custom-level!=-1", "true",
        "@.iat==null", "false",
        "@.custom=~/CUSTOM-.+/i", "true",
        "@.custom=='custom-value' && @.exp>1000", "false",
        "@.custom=='custom-value'&&@.exp>1000", "false"
    };

    /*
     * These queries return a different result in Jayway JsonPath implementation
     *
     * In the case of 'anyof' and 'noneof' Jayway seems to silently fail the evaluation when the referenced
     * attribute is missing, which results in EXPRESSION and !(EXPRESSION) to both effectively evaluate to false.
     */
    final String[] incompatibleJaywayQueries = {
        "!(@.missing anyof [null, 'username'])", "true",
        "!(@.missing noneof [null, 'username'])", "true"
    };

    final static Configuration.Defaults JAYWAY_CONFIG = new Configuration.Defaults() {

        private final JsonProvider jsonProvider = new JacksonJsonNodeJsonProvider();
        private final MappingProvider mappingProvider = new JacksonMappingProvider();

        @Override
        public JsonProvider jsonProvider() {
            return jsonProvider;
        }

        @Override
        public MappingProvider mappingProvider() {
            return mappingProvider;
        }

        @Override
        public Set<Option> options() {
            return EnumSet.noneOf(Option.class);
        }
    };

    @Test
    public void testJsonPathFilterQuery() throws Exception {

        String[] errQueries = {
                "['exp'] > 1000", "attribute path",
                "@", "at position: 0",
                "@.custom in null", "right of 'in'",
                "1 < @.attr", "left of '<'",
                "'lala' > 'lala'", "attribute path",
                "'lala' <= null", "attribute path",
                "@.attr > null", "'null' to the right",
                "@.attr == and !('admin' in @.roles)", "Value expected to the right",
                "@.exp < 'cant compare makes it false", "missing end quote"
        };


        JsonNode json = JsonSerialization.readValue(jsonString, JsonNode.class);

        testQueries(json, queries);
        testQueries(json, incompatibleJaywayQueries);
        testQueries(json, nonStandardQueries);

        for (int i = 0; i < errQueries.length; i++) {
            try {
                String query = errQueries[i];
                System.out.println("Test failing parse: " + query);

                JsonPathFilterQuery.parse(query);
                Assert.fail("Parsing the query should have failed: " + query);
            } catch (JsonPathFilterQueryException expected) {
                try {
                    Assert.assertTrue("Failed to parse", expected.getMessage().contains(errQueries[++i]));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        }
    }

    private void testQueries(JsonNode json, String[] queries) {
        for (int i = 0; i < queries.length; i++) {
            String query = queries[i];
            JsonPathFilterQuery q = JsonPathFilterQuery.parse(query);

            boolean expected = Boolean.parseBoolean(queries[++i]);

            boolean result = q.matches(json);
            System.out.println("Test: " + query + " " + (result ? "MATCH" : "NO MATCH"));

            Assert.assertEquals("Expected " + expected + " when testing: " + query, expected, result);
        }
    }


    @Test
    public void testJaywayCompatibility() throws IOException {

        Configuration.setDefaults(JAYWAY_CONFIG);

        Configuration conf = Configuration.builder()
                .options(Option.SUPPRESS_EXCEPTIONS)
                //.options(Option.AS_PATH_LIST)
                .build();

        JsonNode json = JsonSerialization.readValue(jsonString, JsonNode.class);
        json = wrapToken(json);

        ParseContext ctx = using(conf);

        DocumentContext doc = ctx.parse(json);

        testQueriesWithJayway(doc, queries);
        testQueriesWithJayway(doc, unsupportedStandardQueries);
    }

    private void testQueriesWithJayway(DocumentContext doc, String[] queries) {
        for (int i = 0; i < queries.length; i++) {
            String query = queries[i];
            boolean expected = Boolean.parseBoolean(queries[++i]);

            JsonPath compiled = JsonPath.compile("$[*][?(" + query + ")]");
            ArrayNode result = doc.read(compiled);
            Assert.assertEquals("Unexpected result running: " + query, expected, result.size() == 1);
        }
    }

    private JsonNode wrapToken(JsonNode json) {
        JsonNodeFactory nodeFactory = MAPPER.getNodeFactory();
        return nodeFactory.objectNode().set("token", json);
    }

    @Test
    public void testPerformance() throws Exception {
        String jsonTemplate = "{ " +
            "\"aud\": [\"uma_authorization\", \"kafka\"], " +
            "\"iss\": \"https://auth-server/token\", " +
            "\"iat\": %d, " +
            "\"exp\": 600, " +
            "\"sub\": \"username\", " +
            "\"custom\": \"custom-value\"," +
            "\"roles\": {\"client-roles\": {\"kafka\": [\"kafka-user\"]} }, " +
            "\"custom-level\": 9 " +
            "}";

        StringBuilder jsonString = new StringBuilder();
        Formatter fmt = new Formatter(jsonString, Locale.US);

        ArrayList<JsonNode> tokens = new ArrayList<>();

        long time = System.currentTimeMillis();
        final int count = 10000;
        for (int i = 0; i < count; i++) {
            fmt.format(jsonTemplate, i);
            JsonNode json = JsonSerialization.readValue(jsonString.toString(), JsonNode.class);
            tokens.add(json);
            jsonString.setLength(0);
        }
        System.out.printf("Generated %d unique tokens in %d ms%n", count, System.currentTimeMillis() - time);


        ArrayList<JsonPathFilterQuery> parsedQueries = new ArrayList<>();

        time = System.currentTimeMillis();
        for (int i = 0; i < queries.length; i += 2) {
            parsedQueries.add(JsonPathFilterQuery.parse(queries[i]));
        }
        System.out.printf("Parsed %d unique queries in %d ms%n", queries.length / 2, System.currentTimeMillis() - time);

        System.out.println("\nTest JsonPathFilerQuery\n");
        for (int i = 0; i < parsedQueries.size(); i++) {
            time = System.currentTimeMillis();
            JsonPathFilterQuery query = parsedQueries.get(i);
            for (int j = 0; j < tokens.size(); j++) {
                query.matches(tokens.get(j));
            }
            System.out.printf("Ran query on %d unique tokens in %d ms :: '%s'%n", tokens.size(), System.currentTimeMillis() - time, query);
        }

        // Now compare with jayway JsonPath
        Configuration.setDefaults(JAYWAY_CONFIG);

        Configuration conf = Configuration.builder()
                .options(Option.SUPPRESS_EXCEPTIONS)
                //.options(Option.AS_PATH_LIST)
                .build();


        System.out.println("\nTest Jayway JsonPath\n");

        ArrayList<JsonPath> parsedJaywayQueries = new ArrayList<>();

        time = System.currentTimeMillis();
        for (int i = 0; i < queries.length; i += 2) {
            parsedJaywayQueries.add(JsonPath.compile("$[*][?(" + queries[i] + ")]"));
        }
        System.out.printf("Parsed %d unique queries in %d ms%n", queries.length / 2, System.currentTimeMillis() - time);

        for (int i = 0; i < parsedJaywayQueries.size(); i++) {
            time = System.currentTimeMillis();
            JsonPath query = parsedJaywayQueries.get(i);
            for (int j = 0; j < tokens.size(); j++) {
                JsonNode json = wrapToken(tokens.get(j));

                ParseContext ctx = using(conf);
                DocumentContext doc = ctx.parse(json);
                doc.read(query);
            }
            System.out.printf("Ran query on %d unique tokens in %d ms :: '%s'%n", tokens.size(), System.currentTimeMillis() - time, queries[i*2]);
        }
    }
}
