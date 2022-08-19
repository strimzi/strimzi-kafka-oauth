/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

public class LogKafkaImage {

    public LogKafkaImage() {
        System.out.println("Using Kafka Image: " + System.getProperty("KAFKA_DOCKER_IMAGE"));
    }
}
