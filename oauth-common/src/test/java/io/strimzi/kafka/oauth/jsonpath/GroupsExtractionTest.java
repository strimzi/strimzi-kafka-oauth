/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.JSONUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class GroupsExtractionTest {

    @Test
    public void testJsonPathQuery() throws IOException {
        String[] queryGroupsDelim = {
            "$.groups", "\"group1,group2\"", ",",
            "$.groups", "\"group1, group2\"", ",",
            "$.groups", "\"group1:group2\"", ":",
            "$.groups", "\"group1 group2\"", " ",
            "$.groups", "[\"group1\", \"group2\"]", null,
            "$.groups.realm", "{ \"realm\": [\"group1\", \"group2\"] }", null,
            "$['groups']['realm']", "{ \"realm\": [\"group1\", \"group2\"] }", null
        };

        List<String> groups;
        for (int i = 0; i < queryGroupsDelim.length; i++) {
            groups = getGroupsFromJWT(queryGroupsDelim[i], getJsonStringWithGroups(queryGroupsDelim[++i]), queryGroupsDelim[++i]);
            Assert.assertEquals("Expected [\"group1\", \"group2\"]", Arrays.asList("group1", "group2"), groups);
        }
    }

    private List<String> getGroupsFromJWT(String query, String jsonString, String delimiter) throws IOException {
        JsonNode json = JSONUtil.readJSON(jsonString, JsonNode.class);
        JsonNode result = JsonPathQuery.parse(query).apply(json);
        if (delimiter != null) {
            return JSONUtil.asListOfString(result, delimiter);
        } else {
            return JSONUtil.asListOfString(result);
        }
    }

    private String getJsonStringWithGroups(String groupsFragment) {
        return "{ " +
                "\"aud\": [\"uma_authorization\", \"kafka\"], " +
                "\"iss\": \"https://auth-server/token\", " +
                "\"iat\": 0, " +
                "\"exp\": 600, " +
                "\"sub\": \"username\", " +
                "\"custom\": \"custom-value\"," +
                "\"roles\": {\"client-roles\": {\"kafka\": [\"kafka-user\"]} }, " +
                "\"groups\": " + groupsFragment + ", " +
                "\"custom-level\": 9 " +
                "}";
    }
}
