package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.io.InputStream;

public class JSONUtil {

    public static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T readJSON(InputStream is, Class<T> clazz) throws IOException {
        return mapper.readValue(is, clazz);
    }

    public static String getClaimFromJWT(String claim, Object token) {
        try {
            // No nice way to get arbitrary claim from already parsed token
            // therefore we re-serialise and deserialize into generic json object
            String jsonString = JsonSerialization.writeValueAsString(token);
            JsonNode node = JsonSerialization.readValue(jsonString, JsonNode.class);
            JsonNode claimNode = node.get(claim);

            if (claimNode == null) {
                throw new RuntimeException("Access token contains no '" + claim + "' claim: " + jsonString);
            }

            return claimNode.asText();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read '" + claim + "' claim from token", e);
        }
    }

}
