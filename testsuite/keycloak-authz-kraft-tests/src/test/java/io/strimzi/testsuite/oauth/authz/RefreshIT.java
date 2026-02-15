/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.testsuite.oauth.authz.kraft.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for authorization with permission refresh
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Authorization Refresh Tests")
public class RefreshIT extends BasicIT {

    private KeycloakAuthzKRaftTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    @Override
    void setUp() throws Exception {
        environment = new KeycloakAuthzKRaftTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();
        
        authenticateAllActors();
    }

    @AfterAll
    @Override
    void tearDown() {
        cleanup();
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @Order(8)
    @DisplayName("Change permissions for clients")
    @Tag("authorization")
    @Tag("refresh")
    public void changePermissionsForClients() throws IOException {

        String token = loginWithUsernamePassword(URI.create("http://localhost:8080/realms/master/protocol/openid-connect/token"),
                "admin", "admin", "admin-cli");

        String authorization = "Bearer " + token;

        //  get the id of 'kafka' client
        //
        //  GET http://localhost:8080/admin/realms/kafka-authz/clients?first=0&max=20&search=true

        String clientsUrl = "http://localhost:8080/admin/realms/kafka-authz/clients";
        JsonNode result = HttpUtil.get(URI.create(clientsUrl), authorization, JsonNode.class);

        String clientId = null;

        Iterator<JsonNode> it = result.elements();
        while (it.hasNext()) {
            JsonNode client = it.next();
            if ("kafka".equals(client.get("clientId").asText())) {
                clientId = client.get("id").asText();
                break;
            }
        }

        assertNotNull(clientId, "Client 'kafka'");

        //  get the ids of all the resources - extract the ids of 'Topic:a_*' and 'kafka-cluster:my-cluster,Topic:b_*'

        Map<String, String> resources = getAuthzResources(authorization, clientId);

        String aTopicsId = resources.get("Topic:a_*");
        String bTopicsId = resources.get("kafka-cluster:my-cluster,Topic:b_*");

        assertNotNull(aTopicsId, "Resource for a_* topics");
        assertNotNull(bTopicsId, "Resource for b_* topics");

        //  get the ids of all the action scopes - extract the ids of 'Describe' and 'Write'

        Map<String, String> scopes = getAuthzScopes(authorization, clientId);

        String describeScope = scopes.get("Describe");
        String writeScope = scopes.get("Write");

        assertNotNull(describeScope, "'Describe' scope");
        assertNotNull(writeScope, "'Write' scope'");

        //  get the ids of all the policies - extract the ids of 'Dev Team A' and 'Dev Team B'

        Map<String, String> policies = getAuthzPolicies(authorization, clientId);

        String devTeamA = policies.get("Dev Team A");
        String devTeamB = policies.get("Dev Team B");

        assertNotNull(devTeamA, "'Dev Team A' policy");
        assertNotNull(devTeamB, "'Dev Team B' policy");

        //  get the ids of all the permissions

        String permissionsUrl = "http://localhost:8080/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission";
        result = HttpUtil.get(URI.create(permissionsUrl), authorization, JsonNode.class);

        String devTeamAPermission = null;
        String devTeamBPermission = null;

        it = result.elements();
        while (it.hasNext()) {
            JsonNode permission = it.next();
            if (permission.get("name").asText().startsWith("Dev Team A owns")) {
                devTeamAPermission = permission.get("id").asText();
            } else if (permission.get("name").asText().startsWith("Dev Team B owns")) {
                devTeamBPermission = permission.get("id").asText();
            }
        }

        assertNotNull(devTeamAPermission, "'Dev Team A owns' permission");
        assertNotNull(devTeamBPermission, "'Dev Team B owns' permission");

        //  Grant team-a-client the permission to write to b_* topics,
        //  and team-b-client the permissions to write to a_* topics

        addPermissions(authorization, clientId, describeScope, writeScope, aTopicsId, bTopicsId, devTeamA, devTeamB);

        //  Remove the ownership permissions to a_* topics from team-a-client
        //  and the ownership permissions to b_* topics from team-b-client

        removePermissions(authorization, clientId, devTeamAPermission, devTeamBPermission);
    }

    private void removePermissions(String authorization, String clientId, String devTeamAPermission, String devTeamBPermission) throws IOException {
        String permissionUrl = "http://localhost:8080/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission/" + devTeamAPermission;
        HttpUtil.delete(URI.create(permissionUrl), authorization);

        permissionUrl = "http://localhost:8080/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission/" + devTeamBPermission;
        HttpUtil.delete(URI.create(permissionUrl), authorization);
    }

    private void addPermissions(String authorization, String clientId, String describeScope, String writeScope, String aTopicsId, String bTopicsId, String devTeamA, String devTeamB) throws IOException {

        String bodyPattern = "{\"type\":\"scope\",\"logic\":\"POSITIVE\",\"decisionStrategy\":\"UNANIMOUS\"" +
            ",\"name\":\"%s\",\"resources\":[\"%s\"]" +
            ",\"scopes\":[\"%s\",\"%s\"],\"policies\":[\"%s\"]}";

        String permissionUrl = "http://localhost:8080/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission/scope";

        String body = String.format(bodyPattern, "Dev Team A can write to topics that start with b_",
                bTopicsId, describeScope, writeScope, devTeamA);
        HttpUtil.post(URI.create(permissionUrl), authorization, "application/json", body, JsonNode.class);


        //  Repeat for Dev Team B by using the Topic:a_* resource id, 'Describe' and 'Write' scope ids, and 'Dev Team B' policy id

        body = String.format(bodyPattern, "Dev Team B can write to topics that start with a_",
                aTopicsId, describeScope, writeScope, devTeamB);
        HttpUtil.post(URI.create(permissionUrl), authorization, "application/json", body, JsonNode.class);
    }

    private Map<String, String> getAuthzScopes(String authorization, String clientId) throws IOException {
        String scopesUrl = "http://localhost:8080/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/scope";
        return getAuthzList(URI.create(scopesUrl), authorization, "name", "id");
    }

    private Map<String, String> getAuthzResources(String authorization, String clientId) throws IOException {
        String resourcesUrl = "http://localhost:8080/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/resource";
        return getAuthzList(URI.create(resourcesUrl), authorization, "name", "_id");
    }

    private Map<String, String> getAuthzPolicies(String authorization, String clientId) throws IOException {
        String policiesUrl = "http://localhost:8080/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/policy";
        return getAuthzList(URI.create(policiesUrl), authorization, "name", "id");
    }

    private Map<String, String> getAuthzList(URI url, String authorization, String keyAttr, String valueAttr) throws IOException {
        JsonNode result = HttpUtil.get(url, authorization, JsonNode.class);

        Map<String, String> items = new HashMap<>();
        Iterator<JsonNode> it = result.elements();
        while (it.hasNext()) {
            JsonNode resource = it.next();
            items.put(resource.get(keyAttr).asText(), resource.get(valueAttr).asText());
        }
        return items;
    }

    @Test
    @Order(9)
    @DisplayName("Test changed permissions after refresh")
    @Tag("authorization")
    @Tag("refresh")
    public void testChangedPermissions() throws Exception {
        // wait 15 seconds for permissions changes to take effect on the broker
        Thread.sleep(15000);

        Producer<String, String> teamAProducer = getProducer(TEAM_A_CLIENT);
        //
        // team-a-client should now succeed to produce to b_* topic
        //
        produce(teamAProducer, TOPIC_B);

        //
        // team-a-client should no longer be able to write to a_* topic
        //
        produceFail(teamAProducer, TOPIC_A);


        Producer<String, String> teamBProducer = getProducer(TEAM_B_CLIENT);
        //
        // team-b-client should now succeed to produce to a_* topic
        //
        produce(teamBProducer, TOPIC_A);

        //
        // team-b-client should no longer be able to write to b_* topic
        //
        produceFail(teamBProducer, TOPIC_B);
    }

}
