/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.JSONUtil;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;

public class CustomCheckTest {

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
        "!(@.missing && @.missing anyof [null, 'username'])", "true",
        "!(@.missing && @.missing noneof [null, 'username'])", "true",
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
        "@.custom", "true",
        "@.sub && @.custom == 'custom-value'", "true",
        "@.custom == 'custom-value' && @.roles.client-roles.kafka", "true",
        "@.['custom'] == 'custom-value'", "true",
        "@.custom in ['custom-value','custom-value2','custom-value3']", "true",
        "@.custom-level && @.custom-level nin [1,2,3]", "true",
        "@.missing nin [1,2,3]", "true",
        "@.missing && @.missing nin [1,2,3]", "false",
        "\"kafka-user\" in @.['roles'].['client-roles'].['kafka']", "true",
        "@.roles.client-roles.kafka && \"kafka-admin\" nin @.roles.client-roles.kafka", "true",
        "@.sub == 'username' && ((@.custom &&  @.custom == 'custom-value') || !@.custom)", "true",
        "@.sub == 'username' && ((@.custom &&  @.custom == 'custom-fail') || !@.custom)", "false",
        "@.sub == 'username' && ((@.cus &&  @.cus == 'custom-value') || !@.cus)", "true"
    };

    /*
     * These are queries supported by Jayway implementation but not supported by our implementation.
     * It's mostly the lack of whitespace.
     */
    final String[] poorWhitespaceQueries = {
        "@.custom-level!=-1", "true",
        "@.iat==null", "false",
        "@.custom=~/CUSTOM-.+/i", "true",
        "@.custom=='custom-value' && @.exp>1000", "false",
        "@.custom=='custom-value'&&@.exp>1000", "false"
    };

    /*
     * In the case of 'anyof' and 'noneof' Jayway seems to silently fail the evaluation when the referenced
     * attribute is missing, which results in EXPRESSION and !(EXPRESSION) to both effectively evaluate to false.
     *
     * See: https://github.com/json-path/JsonPath/issues/662
     */
    final String[] maybeBuggyJaywayQueries = {
        "!(@.missing anyof [null, 'username'])", "true",
        "!(@.missing noneof [null, 'username'])", "true"
    };

    final String[] nonsensicalQueries = {
        "['exp'] > 1000", "false",
        "@", "true",
        "@.custom in null", "false",
        "'lala' > 'lala'", "false",
        "'lala' <= null", "false",
        "@.attr > null", "false",
        "1 < @.attr", "false"
    };

    @Test
    public void testJsonPathFilterQuery() throws Exception {

        String[] errQueries = {
            "@.attr == and !('admin' in @.roles)",     // no value to the right of ==
            "@.exp < 'unclosed string"                 // unclosed string
        };

        JsonNode json = JSONUtil.readJSON(jsonString, JsonNode.class);

        testQueries(json, queries);
        testQueries(json, poorWhitespaceQueries);
        testQueries(json, nonsensicalQueries);
        //testQueries(json, maybeBuggyJaywayQueries);

        for (String errQuery : errQueries) {
            try {
                System.out.println("Test failing parse: " + errQuery);

                JsonPathFilterQuery.parse(errQuery);
                Assert.fail("Parsing the query should have failed: " + errQuery);

            } catch (JsonPathQueryException expected) {
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

    @Ignore
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
            JsonNode json = JSONUtil.readJSON(jsonString.toString(), JsonNode.class);
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
        for (JsonPathFilterQuery parsedQuery : parsedQueries) {
            time = System.currentTimeMillis();
            for (JsonNode token : tokens) {
                parsedQuery.matches(token);
            }
            System.out.printf("Ran query on %d unique tokens in %d ms :: '%s'%n", tokens.size(), System.currentTimeMillis() - time, parsedQuery);
        }
    }
}
