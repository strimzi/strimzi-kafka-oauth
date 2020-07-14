/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

enum MockExecutorLogActionType {
    SCHEDULE_WITH_FIXED_DELAY,
    SCHEDULE_AT_FIXED_RATE,
    SCHEDULE,
    INVOKE_ANY,
    INVOKE_ALL,
    SUBMIT,
    EXECUTE
}
