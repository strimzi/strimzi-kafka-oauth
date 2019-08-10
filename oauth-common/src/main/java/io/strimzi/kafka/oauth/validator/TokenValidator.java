package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.common.TokenInfo;

public interface TokenValidator {

    TokenInfo validate(String token);
}
