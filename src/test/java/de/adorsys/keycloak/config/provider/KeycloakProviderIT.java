/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2020 adorsys GmbH & Co. KG @ https://adorsys.de
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package de.adorsys.keycloak.config.provider;

import de.adorsys.keycloak.config.AbstractImportTest;
import de.adorsys.keycloak.config.exception.KeycloakProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeycloakProviderIT extends AbstractImportTest {
    @Override
    @SuppressWarnings("unused")
    public void setup() {
    }
}

@TestPropertySource(properties = {
        "keycloak.url=https://@^",
})
class KeycloakProviderInvalidUrlIT extends KeycloakProviderIT {
    @Test
    void testInvalidUrlException() {
        KeycloakProviderException thrown = assertThrows(KeycloakProviderException.class, keycloakProvider::get);

        assertThat(thrown.getMessage(), is("java.net.URISyntaxException: Illegal character in authority at index 8: https://@^"));
    }
}

@TestPropertySource(properties = {
        "keycloak.url=https://localhost:1",
        "keycloak.availability-check.enabled=true",
        "keycloak.availability-check.timeout=300ms",
        "keycloak.availability-check.retry-delay=100ms",
})
class KeycloakProviderTimeoutIT extends KeycloakProviderIT {
    @Test
    void testTimeout() {
        KeycloakProviderException thrown = assertThrows(KeycloakProviderException.class, keycloakProvider::get);

        assertThat(thrown.getMessage(), matchesPattern("Could not connect to keycloak in 0 seconds: .*$"));
    }
}
