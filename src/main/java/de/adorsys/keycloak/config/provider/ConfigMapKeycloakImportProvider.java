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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.adorsys.keycloak.config.exception.InvalidImportException;
import de.adorsys.keycloak.config.model.KeycloakImport;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.util.ChecksumUtil;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ConfigMapKeycloakImportProvider {
    private static final Logger logger = LoggerFactory.getLogger(ConfigMapKeycloakImportProvider.class);

    private final ImportConfigProperties importConfigProperties;

    public ConfigMapKeycloakImportProvider(
            ImportConfigProperties importConfigProperties
    ) {
        this.importConfigProperties = importConfigProperties;
    }

    public KeycloakImport get(ConfigMap configMap) {
        KeycloakImport keycloakImport;
        keycloakImport = readRealmImportFromConfigMap(configMap);

        return keycloakImport;
    }




    private KeycloakImport readRealmImportFromConfigMap(ConfigMap configMap) {
        Map<String, RealmImport> realmImports = new HashMap<>();

        RealmImport realmImport = readToRealmImport(configMap);
        realmImports.put(configMap.getMetadata().getName(), realmImport);

        return new KeycloakImport(realmImports);
    }


    private RealmImport readToRealmImport(ConfigMap importConfigMap) {
        RealmImport realmImport;

        ImportConfigProperties.ImportFileType fileType = importConfigProperties.getFileType();

        ObjectMapper objectMapper;
        switch (fileType) {
            case YAML:
                objectMapper = new ObjectMapper(new YAMLFactory());
                break;
            case JSON:
                objectMapper = new ObjectMapper();
                break;
            default:
                throw new InvalidImportException("Unknown import data type :" + fileType.toString());
        }

        objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        String configData = importConfigMap.getData().get("configData");

        try {
            realmImport = objectMapper.readValue(configData, RealmImport.class);
        } catch (IOException e) {
            throw new InvalidImportException(e);
        }

        return realmImport;
    }

    private String calculateChecksum(String importData) {
        return ChecksumUtil.checksum(importData.getBytes());
    }
}
