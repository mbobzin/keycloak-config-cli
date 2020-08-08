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

package de.adorsys.keycloak.config;

import de.adorsys.keycloak.config.model.KeycloakImport;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.operator.ConfigMapController;
import de.adorsys.keycloak.config.provider.ConfigMapKeycloakImportProvider;
import de.adorsys.keycloak.config.provider.KeycloakImportProvider;
import de.adorsys.keycloak.config.service.RealmImportService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Component
public class KeycloakConfigRunner implements CommandLineRunner, ExitCodeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakConfigRunner.class);
    private static final long START_TIME = System.currentTimeMillis();

    private final ConfigMapKeycloakImportProvider configMapKeycloakImportProvider;
    private final RealmImportService realmImportService;
    private final KubernetesClient kubernetesClient;

    private int exitCode = 0;

    @Autowired
    public KeycloakConfigRunner(
            RealmImportService realmImportService,
            KubernetesClient kubernetesClient,
            ConfigMapKeycloakImportProvider configMapKeycloakImportProvider
    ) {
        this.realmImportService = realmImportService;
        this.kubernetesClient = kubernetesClient;
        this.configMapKeycloakImportProvider = configMapKeycloakImportProvider;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public void run(String... args) {
        try {
            String namespace = this.kubernetesClient.getNamespace();
            if (namespace == null) {
                logger.info("No namespace found via config, assuming default.");
                namespace = "default";
            }

            SharedInformerFactory informerFactory = kubernetesClient.informers();
            SharedIndexInformer<ConfigMap> configMapSharedIndexInformer =
                    informerFactory.sharedIndexInformerFor(ConfigMap.class, ConfigMapList.class, 10 * 60 * 1000);

            ConfigMapController configMapController = new ConfigMapController(kubernetesClient,
                    configMapKeycloakImportProvider,
                    realmImportService,
                    configMapSharedIndexInformer, namespace);

            configMapController.create();
            informerFactory.startAllRegisteredInformers();
            informerFactory.addSharedInformerEventListener(exception -> logger.error("Exception occurred, but caught", exception));
            configMapController.run();
        } catch (NullPointerException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());

            exitCode = 1;

            if (logger.isDebugEnabled()) {
                throw e;
            }
        } finally {
            long totalTime = System.currentTimeMillis() - START_TIME;
            String formattedTime = new SimpleDateFormat("mm:ss.SSS").format(new Date(totalTime));
            logger.info("keycloak-config-cli running in {}.", formattedTime);
        }
    }
}
