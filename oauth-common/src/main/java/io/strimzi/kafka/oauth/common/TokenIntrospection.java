/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSObject;
import io.strimzi.kafka.oauth.validator.ValidationException;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.TokenInfo.EXP;

/**
 * A class with methods for introspecting a JWT token
 */
public class TokenIntrospection {

    private static final NimbusPayloadTransformer TRANSFORMER = new NimbusPayloadTransformer();

    /**
     * Parse a raw access token, and extract the basic information from it, including user id by using a given {@link PrincipalExtractor}.
     *
     * @param token A raw token
     * @param principalExtractor PrincipalExtractor instance
     * @return Extracted token information as TokenInfo object
     */
    public static TokenInfo introspectAccessToken(String token, PrincipalExtractor principalExtractor) {
        JWSObject jws;
        try {
            jws = JWSObject.parse(token);
        } catch (Exception e) {
            throw new ValidationException("Failed to parse JWT token: " + mask(token));
        }

        try {
            JsonNode parsed = jws.getPayload().toType(TRANSFORMER);

            if (principalExtractor == null) {
                principalExtractor = new PrincipalExtractor();
            }

            String principal = principalExtractor.getPrincipal(parsed);
            if (principal == null) {
                principal = principalExtractor.getSub(parsed);
            }
            return new TokenInfo(parsed, token, principal);

        } catch (Exception e) {
            throw new ValidationException("Failed to read payload from JWT access token", e);
        }
    }

    /**
     * Debug log the given raw token by parsing it as JWT and logging its payload section
     *
     * @param log Logger to use
     * @param token A raw token
     */
    public static void debugLogJWT(Logger log, String token) {
        JWSObject jws;
        try {
            jws = JWSObject.parse(token);
            log.debug("Token: {}", jws.getPayload());
        } catch (Exception e) {
            log.debug("[IGNORED] Token doesn't seem to be JWT token: {}", mask(token));
            return;
        }

        try {
            JsonNode parsed = jws.getPayload().toType(TRANSFORMER);
            JsonNode expires = parsed.get(EXP);
            if (expires == null) {
                log.debug("Access token has no expiry set.");
            } else {
                log.debug("Access token expires at (UTC): " + (expires.isNumber() ? (LocalDateTime.ofEpochSecond(expires.asLong(), 0, ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)) : ("invalid value: [" + expires.asText() + "]")));
            }
        } catch (Exception e) {
            log.debug("[IGNORED] Failed to parse JWT token's payload", e);
        }
    }
}
