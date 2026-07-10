/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

/**
 * Marker interface for JUnit test categorization.
 * Tests annotated with @Category(OKPTestGroup.class) are OKP-related tests
 * that require the 'okp-support' profile to be activated.
 *
 * By default, these tests run along with all other tests. However, you can:
 * - Run ONLY OKP tests: mvn test -Dgroups=io.strimzi.testsuite.oauth.common.OKPTestGroup
 * - Exclude OKP tests: mvn test -DexcludedGroups=io.strimzi.testsuite.oauth.common.OKPTestGroup
 *
 * Note: OKP tests will be automatically skipped if the 'okp-support'
 * profile is not activated, regardless of category filtering.
 */
public interface OKPTestGroup {
}
