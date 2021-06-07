/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.util.Properties;

public class ConfigUtil {

    public static SSLSocketFactory createSSLFactory(Config config) {
        String truststore = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String truststoreData = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_CERTIFICATES);
        String password = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String type = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String rnd = config.getValue(Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        return SSLUtil.createSSLFactory(truststore, truststoreData, password, type, rnd);
    }

    public static HostnameVerifier createHostnameVerifier(Config config) {
        String hostCheck = config.getValue(Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "HTTPS");

        // Following Kafka convention for skipping hostname validation (when set to <empty>)
        return "".equals(hostCheck) ? SSLUtil.createAnyHostHostnameVerifier() : null;
    }

    public static void putIfNotNull(Properties p, String key, Object value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    public static String getConfigWithFallbackLookup(Config c, String key, String fallbackKey) {
        String result = c.getValue(key);
        if (result == null) {
            result = c.getValue(fallbackKey);
        }
        return result;
    }
}
