package de.adorsys.keycloak.config.operator;

import de.adorsys.keycloak.config.model.KeycloakImport;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.provider.ConfigMapKeycloakImportProvider;
import de.adorsys.keycloak.config.service.RealmImportService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ConfigMapController {
    private static final Logger logger = LoggerFactory.getLogger(ConfigMapController.class);

    private final ConfigMapKeycloakImportProvider configMapKeycloakImportProvider;
    private final RealmImportService realmImportService;
    private BlockingQueue<String> workqueue;
    private SharedIndexInformer<ConfigMap> configMapInformer;
    private Lister<ConfigMap> configMapLister;
    private KubernetesClient kubernetesClient;

    public ConfigMapController(KubernetesClient kubernetesClient,
                               ConfigMapKeycloakImportProvider configMapKeycloakImportProvider,
                               RealmImportService realmImportService,
                               SharedIndexInformer<ConfigMap> configMapInformer, String namespace) {
        this.kubernetesClient = kubernetesClient;
        this.configMapLister = new Lister<>(configMapInformer.getIndexer(), namespace);
        this.configMapInformer = configMapInformer;
        this.configMapKeycloakImportProvider = configMapKeycloakImportProvider;
        this.realmImportService = realmImportService;
        this.workqueue = new ArrayBlockingQueue<>(1024);
    }

    public void create() {
        configMapInformer.addEventHandler(new ResourceEventHandler<ConfigMap>() {
            @Override
            public void onAdd(ConfigMap configMap) {
                enqueueConfigMap(configMap);
            }

            @Override
            public void onUpdate(ConfigMap configMap, ConfigMap t1) {
                enqueueConfigMap(configMap);
            }

            @Override
            public void onDelete(ConfigMap configMap, boolean b) {
                enqueueConfigMap(configMap);
            }
        });
    }

    public void run() {
        logger.info("Start ConfigMap controller");
        while (!configMapInformer.hasSynced()) {
            // Wait till Informer syncs
        }

        while (true) {
            try {
                logger.info("trying to fetch items from workqueue...");
                if(workqueue.isEmpty()) {
                    logger.info("workqueue is empty.");
                    continue;
                }
                String key = workqueue.take();
                Objects.requireNonNull(key, "key can't be null");
                logger.info(String.format("Got %s", key));
                if(key.isEmpty() || (!key.contains("/"))) {
                    logger.warn(String.format("invalid resource key: %s", key));
                    continue;
                }

                // Get the ConfigMap resource's name from key which is in format namespace/name
                String name = key.split("/")[1];
                ConfigMap configMap = configMapLister.get(name);
                if(configMap == null) {
                    logger.info(String.format("ConfigMap %s in workqueue no longer exists", name));
                    return;
                }
                reconcile(configMap);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("ConfigMapController interrupted...");
            }
        }
    }

    protected void reconcile(ConfigMap configMap) {
        KeycloakImport keycloakImport = this.configMapKeycloakImportProvider.get(configMap);
        Map<String, RealmImport> realmImports = keycloakImport.getRealmImports();

        for (Map.Entry<String, RealmImport> realmImport : realmImports.entrySet()) {
            realmImportService.doImport(realmImport.getValue());
        }

    }

    private void enqueueConfigMap(ConfigMap configMap) {
        logger.info("enqueueConfigMap(" + configMap.getMetadata().getName() + ")");
        String key = Cache.metaNamespaceKeyFunc(configMap);
        logger.info(String.format("Going to enqueue key %s", key));
        if (key != null && !key.isEmpty()) {
            logger.info("Adding item to workqueue");
            workqueue.add(key);
        }
    }


}
