/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PrincipalExtractorTest {

    @Test
    public void testToStringMethod() {

        PrincipalExtractor extractor = new PrincipalExtractor("username.claim", null, null, null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username.claim, usernamePrefix: null, fallbackUsernameClaim: null, fallbackUsernamePrefix: null}", extractor.toString());

        extractor = new PrincipalExtractor("['username'].['claim']", "admins_", null, "client_");
        Assert.assertEquals("PrincipalExtractor {usernameClaim: ['username'].['claim'], usernamePrefix: admins_, fallbackUsernameClaim: null, fallbackUsernamePrefix: client_}", extractor.toString());

        extractor = new PrincipalExtractor("username", null, "user.id", null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username, usernamePrefix: null, fallbackUsernameClaim: user.id, fallbackUsernamePrefix: null}", extractor.toString());

        extractor = new PrincipalExtractor("username", "intra_", "['user.id']", "intra_service-account-");
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username, usernamePrefix: intra_, fallbackUsernameClaim: ['user.id'], fallbackUsernamePrefix: intra_service-account-}", extractor.toString());
    }

    @Test
    public void testJsonPathFunctionIsThreadSafe() throws Exception {
        // The broker shares one PrincipalExtractor across all network threads of a listener
        PrincipalExtractor extractor = new PrincipalExtractor("$.concat($['ns'], \".\", $['sa'])", null, null, null, true);
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                JsonNode token = JSONUtil.readJSON("{\"ns\": \"ns" + i + "\", \"sa\": \"sa" + i + "\"}", JsonNode.class);
                String expected = "ns" + i + ".sa" + i;
                tasks.add(() -> {
                    int wrong = 0;
                    for (int k = 0; k < 5000; k++) {
                        if (!expected.equals(extractor.getPrincipal(token))) {
                            wrong++;
                        }
                    }
                    return wrong;
                });
            }
            int wrong = 0;
            for (Future<Integer> f : executor.invokeAll(tasks)) {
                wrong += f.get();
            }
            Assert.assertEquals("Principals extracted from another thread's token", 0, wrong);
        } finally {
            executor.shutdown();
        }
    }
}
