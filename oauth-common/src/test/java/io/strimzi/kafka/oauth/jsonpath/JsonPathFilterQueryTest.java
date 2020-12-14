/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class JsonPathFilterQueryTest {

    String jsonString = "{ " +
            "\"aud\": [\"uma_authorization\", \"kafka\"], " +
            "\"iss\": \"https://keycloak/token\", " +
            "\"iat\": 0, " +
            "\"exp\": 600, " +
            "\"sub\": \"username\", " +
            "\"custom\": \"custom-value\"," +
            "\"roles\": {\"client-roles\": {\"kafka\": [\"kafka-user\"]} }, " +
            "\"custom-level\": 9 " +
            "}";

    @Test
    public void testJsonPathFilterQuery() throws IOException {

        String[] queries = {
//            "@.exp < 1000", "true",
//            "@.exp > 1000", "false",
//            "@.custom == 'custom-value'", "true",
//            "@.custom == 'custom-value' and @.exp > 1000", "false",
//            "@.custom == 'custom-value' or @.exp > 1000", "true",
//            "@.custom == 'custom-value' && @.exp <= 1000", "true",
//            "@.custom != 'custom-value'", "false",
//            "@.iat != null", "true",
//            "@.iat == null", "false",
//            "@.custom in ['some-custom-value', 42, 'custom-value']", "true",
//            "@.custom nin ['some-custom-value', 42, 'custom-value']", "false",
//            "@.custom nin []", "true",
//            "@.custom in []", "false",
//            "@.custom-level in [1,2,3]", "false",
//            "@.custom-level in [1,8,9,20]", "true",
//            "@.custom-level nin [1,8,9,20]", "false",
//            "@.roles.client-roles.kafka != null", "true",
//            "'kafka' in @.aud", "true",
//            "\"kafka-user\" in @.roles.client-roles.kafka", "true",
//            "@.exp > 1000 || 'kafka' in @.aud", "true",
//            "(@.custom == 'custom-value' or @.custom == 'custom-value2')", "true",
//            "(@.exp > 1000 && @.custom == 'custom-value') or @.roles.client-roles.kafka != null", "true",
//            "@.exp >= 600 and ('kafka' in @.aud || @.custom == 'custom-value')", "true",
            "((@.custom == 'some-custom-value' || 'kafka' in @.aud) and @.exp > 1000)", "false",
            "((('kafka' in @.aud || @.custom == 'custom-value') and @.exp > 1000))", "false"
        };

        String[] errQueries = {
            "['exp'] > 1000", "attribute path",
            "@", "at position: 0",
            "@.custom in null", "right of 'in'",
            "1 < @.attr", "left of '<'",
            "'lala' > 'lala'", "attribute path",
            "'lala' <= null", "attribute path",
            "@.attr > null", "'null' to the right"
        };

        // TODO: Unsupported comparison

        JsonNode json = JsonSerialization.readValue(jsonString, JsonNode.class);

        for (int i = 0; i < queries.length; i++) {
            String query = queries[i];
            JsonPathFilterQuery q = JsonPathFilterQuery.parse(query);

            boolean expected = Boolean.parseBoolean(queries[++i]);

            boolean result = q.matches(json);
            System.out.println("Test: " + query + " " + (result ? "MATCH" : "NO MATCH"));

            Assert.assertEquals("Expected " + expected + " when testing: " + query, expected, result);
        }

        for (int i = 0; i < errQueries.length; i++) {
            try {
                String query = errQueries[i];
                JsonPathFilterQuery.parse(query);

                Assert.fail("Parsing the query should have failed: " + query);
            } catch (JsonPathFilterQueryException expected) {
                Assert.assertTrue("Failed to parse", expected.getMessage().contains(errQueries[++i]));
            }
        }
    }

    final List<String> tracker = new LinkedList<>();

    @Test
    public void testPrecedence() {
        // we expect the last eval to not be called and expression to resolve to true
        boolean val = evalTrue("first")
                && evalTrue("second")
                || evalFalse("third");
        System.out.println("true && true || false : " + val);

        tracker.clear();

        // we expect the second eval to not be called and expression to resolve to true
        val = evalTrue("first") || evalFalse("second") && evalTrue("third");
        System.out.println("true || false && true" + val);

        tracker.clear();

        // we expect all the evals to be called and expression to resolve to false
        val = evalFalse("first") || evalTrue("second") && evalFalse("third");
        System.out.println("false || true && false: " + val);
    }

    private boolean evalTrue(String label) {
        tracker.add(label);
        return true;
    }

    private boolean evalFalse(String label) {
        tracker.add(label);
        return false;
    }
}
