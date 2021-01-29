/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.HttpUtil;
import org.apache.kafka.clients.producer.Producer;
import org.junit.Assert;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RefreshTest extends BasicTest {

    RefreshTest(String kafkaBootstrap, boolean oauthOverPlain) {
        super(kafkaBootstrap, oauthOverPlain);
    }

    public void doTest() throws Exception {

        tokens = authenticateAllActors();

        testTeamAClientPart1();

        testTeamBClientPart1();

        createTopicAsClusterManager();

        testTeamAClientPart2();

        testTeamBClientPart2();

        testClusterManager();

        changePermissionsForClients();

        // wait 15 seconds for permissions changes to take effect on the broker
        Thread.sleep(15000);

        testChangedPermissions();

        cleanup();
    }

    private void changePermissionsForClients() throws IOException {

        String token = loginWithUsernamePassword(URI.create("http://keycloak:8080/auth/realms/master/protocol/openid-connect/token"),
                "admin", "admin", "admin-cli");

        String authorization = "Bearer " + token;

        //  get the id of 'kafka' client
        //
        //  GET http://localhost:8080/auth/admin/realms/kafka-authz/clients?first=0&max=20&search=true
        //
        //  [{"id":"17d150dc-471d-464f-bb45-c038b573e313","clientId":"account","name":"${client_account}","rootUrl":"${authBaseUrl}","baseUrl":"/realms/kafka-authz/account/","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","defaultRoles":["view-profile","manage-account"],"redirectUris":["/realms/kafka-authz/account/*"],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":true,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,"publicClient":false,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":false,"nodeReRegistrationTimeout":0,"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"0213b06d-3b2b-405e-8b40-d5c0ae59d234","clientId":"account-console","name":"${client_account-console}","rootUrl":"${authBaseUrl}","baseUrl":"/realms/kafka-authz/account/","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":["/realms/kafka-authz/account/*"],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":true,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,"publicClient":true,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{"pkce.code.challenge.method":"S256"},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":false,"nodeReRegistrationTimeout":0,"protocolMappers":[{"id":"176454d1-0065-462a-94e7-9b37fcfea4ab","name":"audience resolve","protocol":"openid-connect","protocolMapper":"oidc-audience-resolve-mapper","consentRequired":false,"config":{}}],"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"d8338593-961f-4039-8705-22914ac44603","clientId":"admin-cli","name":"${client_admin-cli}","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":[],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":true,"serviceAccountsEnabled":false,"publicClient":true,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":false,"nodeReRegistrationTimeout":0,"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"018d3790-f070-4c0f-9b45-600b690b78d8","clientId":"broker","name":"${client_broker}","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":[],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":true,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,"publicClient":false,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":false,"nodeReRegistrationTimeout":0,"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","clientId":"kafka","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":[],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":true,"serviceAccountsEnabled":true,"authorizationServicesEnabled":true,"publicClient":false,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":true,"nodeReRegistrationTimeout":-1,"protocolMappers":[{"id":"49b90fc0-6b50-4528-8988-e5af613ba2b1","name":"Client ID","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientId","id.token.claim":"true","access.token.claim":"true","claim.name":"clientId","jsonType.label":"String"}},{"id":"d52c6693-ee15-462a-95ba-3d2329d84805","name":"Client Host","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientHost","id.token.claim":"true","access.token.claim":"true","claim.name":"clientHost","jsonType.label":"String"}},{"id":"34f58858-e21f-4c9b-b091-e3c68dca1307","name":"Client IP Address","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientAddress","id.token.claim":"true","access.token.claim":"true","claim.name":"clientAddress","jsonType.label":"String"}}],"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"79a562db-f666-401f-bd34-a27fc1183ad5","clientId":"kafka-cli","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":[],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":true,"serviceAccountsEnabled":false,"publicClient":true,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":true,"nodeReRegistrationTimeout":-1,"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"cfcb911f-9dd4-4f86-bc45-3468b4515df9","clientId":"realm-management","name":"${client_realm-management}","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":[],"webOrigins":[],"notBefore":0,"bearerOnly":true,"consentRequired":false,"standardFlowEnabled":true,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,"publicClient":false,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":false,"nodeReRegistrationTimeout":0,"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"488f2812-8efd-4626-9df5-37d3e31a2ad4","clientId":"security-admin-console","name":"${client_security-admin-console}","rootUrl":"${authAdminUrl}","baseUrl":"/admin/kafka-authz/console/","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":["/admin/kafka-authz/console/*"],"webOrigins":["+"],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":true,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,"publicClient":true,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{"pkce.code.challenge.method":"S256"},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":false,"nodeReRegistrationTimeout":0,"protocolMappers":[{"id":"64b56942-4724-4b9a-b349-f817de5b34d3","name":"locale","protocol":"openid-connect","protocolMapper":"oidc-usermodel-attribute-mapper","consentRequired":false,"config":{"userinfo.token.claim":"true","user.attribute":"locale","id.token.claim":"true","access.token.claim":"true","claim.name":"locale","jsonType.label":"String"}}],"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"7f99c353-23a6-49c4-9243-5b033acc9b38","clientId":"team-a-client","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":[],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":true,"serviceAccountsEnabled":true,"publicClient":false,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":true,"nodeReRegistrationTimeout":-1,"protocolMappers":[{"id":"475b3b86-9abd-4f42-a985-9d2100a20a8c","name":"Client IP Address","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientAddress","id.token.claim":"true","access.token.claim":"true","claim.name":"clientAddress","jsonType.label":"String"}},{"id":"72b74e2e-8c03-4cfd-aa4d-f81739a0f337","name":"Client ID","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientId","id.token.claim":"true","access.token.claim":"true","claim.name":"clientId","jsonType.label":"String"}},{"id":"d69c1bd7-ad2a-4d9b-b89e-e4d7605021c3","name":"Client Host","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientHost","id.token.claim":"true","access.token.claim":"true","claim.name":"clientHost","jsonType.label":"String"}}],"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}},{"id":"d6770bfa-e4b8-43bd-9973-b63ab34da150","clientId":"team-b-client","surrogateAuthRequired":false,"enabled":true,"alwaysDisplayInConsole":false,"clientAuthenticatorType":"client-secret","redirectUris":[],"webOrigins":[],"notBefore":0,"bearerOnly":false,"consentRequired":false,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":true,"serviceAccountsEnabled":true,"publicClient":false,"frontchannelLogout":false,"protocol":"openid-connect","attributes":{},"authenticationFlowBindingOverrides":{},"fullScopeAllowed":true,"nodeReRegistrationTimeout":-1,"protocolMappers":[{"id":"0f7e822f-2170-4f54-8e6d-6e514d48b26e","name":"Client IP Address","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientAddress","id.token.claim":"true","access.token.claim":"true","claim.name":"clientAddress","jsonType.label":"String"}},{"id":"ce6ee84f-4a6a-4969-bebe-7caf3b066680","name":"Client ID","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientId","id.token.claim":"true","access.token.claim":"true","claim.name":"clientId","jsonType.label":"String"}},{"id":"fddf832d-1e35-4707-961d-58537d6b7fc7","name":"Client Host","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper","consentRequired":false,"config":{"user.session.note":"clientHost","id.token.claim":"true","access.token.claim":"true","claim.name":"clientHost","jsonType.label":"String"}}],"defaultClientScopes":["web-origins","role_list","roles","profile","email"],"optionalClientScopes":["address","phone","offline_access","microprofile-jwt"],"access":{"view":true,"configure":true,"manage":true}}]

        String clientsUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients";
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

        Assert.assertNotNull("Client 'kafka'", clientId);

        //  get the ids of all the resources - extract the ids of 'Topic:a_*' and 'kafka-cluster:cluster2,Topic:b_*'
        //
        //  GET http://localhost:8080/auth/admin/realms/kafka-authz/clients/f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8/authz/resource-server/resource?deep=false&first=0&max=20
        //
        //  [{"name":"Cluster:*","type":"Cluster","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"_id":"4ba45366-17b8-4087-8f04-61dcfe955a19","uris":[]},{"name":"Group:*","type":"Group","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Any group","_id":"299327bc-d97c-4e09-abac-cad81370d79d","uris":[]},{"name":"Group:a_*","type":"Group","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Groups that start with a_","_id":"f97f031a-367b-47c5-bb93-c60362c56e0f","uris":[]},{"name":"Group:x_*","type":"Group","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Consumer groups that start with x_","_id":"4dcaa3f7-6b07-401e-8d23-a7616e7a4b1c","uris":[]},{"name":"Topic:*","type":"Topic","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Any topic","_id":"3dd0074b-b155-4e8c-872a-16033a1927fd","uris":[]},{"name":"Topic:a_*","type":"Topic","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Topics that start with a_","_id":"32a72563-080b-4c0e-a149-0ae0b52dcec9","uris":[]},{"name":"Topic:x_*","type":"Topic","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Topics that start with x_","_id":"b89421df-3e16-4256-891c-ab0914abfd16","uris":[]},{"name":"kafka-cluster:cluster2,Cluster:*","type":"Cluster","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Cluster scope on cluster2","_id":"d15001bd-2362-4fd1-8356-b538ae29935e","uris":[]},{"name":"kafka-cluster:cluster2,Group:*","type":"Group","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Any group on cluster2","_id":"b56c6111-1a0b-41b0-8b6d-bc10e6929ac3","uris":[]},{"name":"kafka-cluster:cluster2,Topic:*","type":"Topic","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"displayName":"Any topic on cluster2","_id":"018d340f-76cb-4af9-87ef-effc2fc6f341","uris":[]},{"name":"kafka-cluster:cluster2,Topic:b_*","type":"Topic","owner":{"id":"f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8","name":"kafka"},"ownerManagedAccess":false,"_id":"cc9d9f37-d7a0-4227-803d-456db28b264c","uris":[]}]

        Map<String, String> resources = getAuthzResources(authorization, clientId);

        String aTopicsId = resources.get("Topic:a_*");
        String bTopicsId = resources.get("kafka-cluster:cluster2,Topic:b_*");

        Assert.assertNotNull("Resource for a_* topics", aTopicsId);
        Assert.assertNotNull("Resource for b_* topics", bTopicsId);

        //  get the ids of all the action scopes - extract the ids of 'Describe' and 'Write'
        //
        //  GET http://localhost:8080/auth/admin/realms/kafka-authz/clients/f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8/authz/resource-server/scope?deep=false&first=0&max=20
        //
        //  [{"id":"d83d4331-1f0d-42b5-85f0-92dd151fb714","name":"Alter"},{"id":"2e06038d-2fd7-4d28-9b4a-df99bf70061a","name":"AlterConfigs"},{"id":"f9e931a9-3837-4d4d-bf4f-669482dcfc1e","name":"ClusterAction"},{"id":"a79302ba-6e19-4dfb-9f44-96f0b47d1974","name":"Create"},{"id":"fd8dad1f-72ec-4aa5-8942-d257cd2491a4","name":"Delete"},{"id":"a4cb81e8-fb23-4507-a23b-d1720f51140f","name":"Describe"},{"id":"eb3a8e4d-1cda-418d-8e16-f374f3868fe5","name":"DescribeConfigs"},{"id":"a7790937-9114-4d0f-9dc2-6072a9639844","name":"IdempotentWrite"},{"id":"3390f8f5-e2f7-48b4-91a0-273bfb9ff70c","name":"Read"},{"id":"5146366c-e26b-4ad9-8afc-31bd0055fe36","name":"Write"}]

        Map<String, String> scopes = getAuthzScopes(authorization, clientId);

        String describeScope = scopes.get("Describe");
        String writeScope = scopes.get("Write");

        Assert.assertNotNull("'Describe' scope", describeScope);
        Assert.assertNotNull("'Write' scope'", writeScope);

        //  get the ids of all the policies - extract the ids of 'Dev Team A' and 'Dev Team B'
        //
        //  GET http://localhost:8080/auth/admin/realms/kafka-authz/clients/f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8/authz/resource-server/policy?first=0&max=20&permission=false
        //
        //  [{"id":"b62eeb43-cb4c-46e7-9689-b1133d751259","name":"ClusterManager Group","type":"group","logic":"POSITIVE","decisionStrategy":"UNANIMOUS","config":{"groups":"[{\"id\":\"7fd99c0b-feb0-4ff3-b1dc-8a2771bb80fa\",\"extendChildren\":false}]"}},{"id":"4029b042-4ee9-4eff-89c4-705177980f1b","name":"ClusterManager of cluster2 Group","type":"group","logic":"POSITIVE","decisionStrategy":"UNANIMOUS","config":{"groups":"[{\"id\":\"15035a89-f2c8-4aa6-a5ba-58f741c0ab20\",\"extendChildren\":false}]"}},{"id":"836b4fa0-c08f-4a30-be3a-cd80b7804348","name":"Default Policy","description":"A policy that grants access only for users within this realm","type":"js","logic":"POSITIVE","decisionStrategy":"AFFIRMATIVE","config":{"code":"// by default, grants any permission associated with this policy\n$evaluation.grant();\n"}},{"id":"1e9d9afe-a379-4f46-9d1a-9a7b10131110","name":"Dev Team A","type":"role","logic":"POSITIVE","decisionStrategy":"UNANIMOUS","config":{"roles":"[{\"id\":\"31e22e14-35c9-4dab-880f-50aef22de65c\",\"required\":true}]"}},{"id":"64a5b2d7-5aad-4e84-bedc-d49a851604aa","name":"Dev Team B","type":"role","logic":"POSITIVE","decisionStrategy":"UNANIMOUS","config":{"roles":"[{\"id\":\"58df35c5-6fae-43dc-9d25-5f50de22d8d8\",\"required\":true}]"}},{"id":"a1f3c6b5-1cdc-4886-a62f-a24d1404090e","name":"Ops Team","type":"role","logic":"POSITIVE","decisionStrategy":"UNANIMOUS","config":{"roles":"[{\"id\":\"b24efefd-dc67-4209-a65f-4d710a0b235c\",\"required\":true}]"}}]

        Map<String, String> policies = getAuthzPolicies(authorization, clientId);

        String devTeamA = policies.get("Dev Team A");
        String devTeamB = policies.get("Dev Team B");

        Assert.assertNotNull("'Dev Team A' policy", devTeamA);
        Assert.assertNotNull("'Dev Team B' policy", devTeamB);

        //  get the ids of all the permissions
        //
        //  GET http://localhost:8080/auth/admin/realms/kafka-authz/clients/32795fd2-0438-4593-b1d1-26939c89c1fa/authz/resource-server/permission?first=0&max=20
        //
        //  [{"id":"d70bddaa-87bd-4bcd-ad78-be5fbb9cdce5","name":"ClusterManager Group has full access to cluster config","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"71738668-69ed-4348-a387-bcec52d82bb0","name":"ClusterManager Group has full access to manage and affect groups","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"55bc1419-41c2-4644-a2b4-676fd142b748","name":"ClusterManager Group has full access to manage and affect topics","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"a7ad2f09-532d-4f69-93d3-99a8b832d290","name":"ClusterManager of cluster2 Group has full access to cluster config on cluster2","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"21aaff2f-1195-4819-a369-c79d62f4fa93","name":"ClusterManager of cluster2 Group has full access to consumer groups on cluster2","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"a32879e4-90f7-4e5e-9be6-b90119e1078f","name":"ClusterManager of cluster2 Group has full access to topics on cluster2","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"f4122657-bebd-447d-9bb8-f0daaffecbbd","name":"Dev Team A can use consumer groups that start with a_ on any cluster","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"4fe8f3fc-ab62-4a5c-819d-4c854297a15f","name":"Dev Team A can write to topics that start with x_ on any cluster","type":"scope","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"72583910-d3c5-4c09-a84b-8301520f2dd8","name":"Dev Team A owns topics that start with a_ on any cluster","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"fddc0f62-0e56-47ed-abb7-d67d06496189","name":"Dev Team B can read from topics that start with x_ on any cluster","type":"scope","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"0ec06a05-2f52-4387-bd59-bec07b76b839","name":"Dev Team B can update consumer group offsets that start with x_ on any cluster","type":"scope","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"},{"id":"8ccbc5dc-2855-4e5c-abca-214d6c87f807","name":"Dev Team B owns topics that start with b_ on cluster cluster2","type":"resource","logic":"POSITIVE","decisionStrategy":"UNANIMOUS"}]

        String permissionsUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission";
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

        Assert.assertNotNull("'Dev Team A owns' permission", devTeamAPermission);
        Assert.assertNotNull("'Dev Team B owns' permission", devTeamBPermission);

        //  Grant team-a-client the permission to write to b_* topics,
        //  and team-b-client the permissions to write to a_* topics

        addPermissions(authorization, clientId, describeScope, writeScope, aTopicsId, bTopicsId, devTeamA, devTeamB);

        //  Remove the ownership permissions to a_* topics from team-a-client
        //  and the ownership permissions to b_* topics from team-b-client

        removePermissions(authorization, clientId, devTeamAPermission, devTeamBPermission);
    }

    private void removePermissions(String authorization, String clientId, String devTeamAPermission, String devTeamBPermission) throws IOException {
        String permissionUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission/" + devTeamAPermission;
        HttpUtil.delete(URI.create(permissionUrl), authorization);

        permissionUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission/" + devTeamBPermission;
        HttpUtil.delete(URI.create(permissionUrl), authorization);
    }

    private void addPermissions(String authorization, String clientId, String describeScope, String writeScope, String aTopicsId, String bTopicsId, String devTeamA, String devTeamB) throws IOException {

        //  Create a new scope permission using the cluster-name:cluster2,Topic:b_* resource id, 'Describe' and 'Write' scope ids, and 'Dev Team A' policy id
        //
        //  POST http://localhost:8080/auth/admin/realms/kafka-authz/clients/f71ed8e3-8a4a-47f0-b2b5-0971bd93cea8/authz/resource-server/permission/scope
        //
        //  {"type":"scope","logic":"POSITIVE","decisionStrategy":"UNANIMOUS","name":"Team B client can produce to topics starting with a_","resources":["32a72563-080b-4c0e-a149-0ae0b52dcec9"],"scopes":["5146366c-e26b-4ad9-8afc-31bd0055fe36","a4cb81e8-fb23-4507-a23b-d1720f51140f"],"policies":["64a5b2d7-5aad-4e84-bedc-d49a851604aa"]}

        String bodyPattern = "{\"type\":\"scope\",\"logic\":\"POSITIVE\",\"decisionStrategy\":\"UNANIMOUS\"" +
            ",\"name\":\"%s\",\"resources\":[\"%s\"]" +
            ",\"scopes\":[\"%s\",\"%s\"],\"policies\":[\"%s\"]}";

        String permissionUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/permission/scope";

        String body = String.format(bodyPattern, "Dev Team A can write to topics that start with b_",
                bTopicsId, describeScope, writeScope, devTeamA);
        HttpUtil.post(URI.create(permissionUrl), authorization, "application/json", body, JsonNode.class);


        //  Repeat for Dev Team B by using the Topic:a_* resource id, 'Describe' and 'Write' scope ids, and 'Dev Team B' policy id

        body = String.format(bodyPattern, "Dev Team B can write to topics that start with a_",
                aTopicsId, describeScope, writeScope, devTeamB);
        HttpUtil.post(URI.create(permissionUrl), authorization, "application/json", body, JsonNode.class);
    }

    private Map<String, String> getAuthzScopes(String authorization, String clientId) throws IOException {
        String scopesUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/scope";
        return getAuthzList(URI.create(scopesUrl), authorization, "name", "id");
    }

    private Map<String, String> getAuthzResources(String authorization, String clientId) throws IOException {
        String resourcesUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/resource";
        return getAuthzList(URI.create(resourcesUrl), authorization, "name", "_id");
    }

    private Map<String, String> getAuthzPolicies(String authorization, String clientId) throws IOException {
        String policiesUrl = "http://keycloak:8080/auth/admin/realms/kafka-authz/clients/" + clientId + "/authz/resource-server/policy";
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

    private void testChangedPermissions() throws Exception {
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
