package fr.corentin.guacamole.kubernetes.discovery;

import com.google.inject.Inject;
import fr.corentin.guacamole.kubernetes.ConfigurationService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MachineDiscovery {

    @Inject
    private ConfigurationService confService;
    private ApiClient client;
    private CoreV1Api coreV1Api;
    private SharedInformerFactory informerFactory;
    private final Map<String, Connection> connections;

    static final String PROTOCOL_ANNOTATION = "org.apache.guacamole/protocol";
    static final String ANNOTATIONS_PREFIX = "org.apache.guacamole/";
    static final Logger logger = LoggerFactory.getLogger(MachineDiscovery.class);

    public MachineDiscovery() {
        connections = new ConcurrentHashMap<>();
    }

    private boolean hasGuacamoleAnnotations(KubernetesObject kubernetesObject) {
        return kubernetesObject.getMetadata() != null
                && kubernetesObject.getMetadata().getAnnotations() != null
                && kubernetesObject.getMetadata().getAnnotations().containsKey(PROTOCOL_ANNOTATION);
    }

    public void start() throws GuacamoleException {
        try {
            client = Config.defaultClient();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        coreV1Api = new CoreV1Api(client);

        informerFactory = new SharedInformerFactory(client);

        setupPodInformer();
        setupServiceInformer();

        informerFactory.startAllRegisteredInformers();
    }

    private void setupPodInformer() throws GuacamoleException {
        SharedIndexInformer<V1Pod> podInformer;

        if (!confService.getSelectedNamespace().isEmpty()) {
            String selectedNamespace = confService.getSelectedNamespace();
            podInformer = informerFactory.sharedIndexInformerFor(
                    (CallGeneratorParams params) -> {
                        return coreV1Api.listNamespacedPodCall(
                                selectedNamespace,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                params.resourceVersion,
                                null,
                                null,
                                params.timeoutSeconds,
                                params.watch,
                                null);
                    },
                    V1Pod.class,
                    V1PodList.class);


        } else {
            GenericKubernetesApi podClient = new GenericKubernetesApi<>(
                    V1Pod.class,
                    V1PodList.class,
                    "",
                    "v1",
                    "pods",
                    client);

            podInformer = informerFactory.sharedIndexInformerFor(podClient, V1Pod.class, 10000);
        }

        podInformer.addEventHandler(new ResourceEventHandler<V1Pod>() {
            @Override
            public void onAdd(V1Pod pod) {
                if (pod.getStatus() == null || pod.getStatus().getPodIP() == null) {
                    return;
                }
                if (!hasGuacamoleAnnotations(pod)) {
                    return;
                }
                try {
                    connections.put(Utils.objectKey(pod), Utils.connectionAdapter(pod, coreV1Api));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUpdate(V1Pod oldPod, V1Pod newPod) {
                if (newPod.getStatus() == null || newPod.getStatus().getPodIP() == null) {
                    return;
                }
                if (!hasGuacamoleAnnotations(newPod)) {
                    connections.remove(Utils.objectKey(newPod));
                    return;
                }
                try {
                    connections.put(Utils.objectKey(newPod), Utils.connectionAdapter(newPod, coreV1Api));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDelete(V1Pod pod, boolean deletedFinalStateUnknown) {
                if (pod.getStatus() == null || pod.getStatus().getPodIP() == null) {
                    return;
                }
                connections.remove(Utils.objectKey(pod));
            }
        });
    }

    private void setupServiceInformer() throws GuacamoleException {
        SharedIndexInformer<V1Service> serviceInformer;

        if (!confService.getSelectedNamespace().isEmpty()) {
            String selectedNamespace = confService.getSelectedNamespace();
            serviceInformer = informerFactory.sharedIndexInformerFor(
                            (CallGeneratorParams params) -> {
                                return coreV1Api.listNamespacedServiceCall(
                                        selectedNamespace,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        params.resourceVersion,
                                        null,
                                        null,
                                        params.timeoutSeconds,
                                        params.watch,
                                        null);
                            },
                            V1Service.class,
                            V1ServiceList.class);
        } else {
            GenericKubernetesApi serviceClient = new GenericKubernetesApi<>(
                    V1Service.class,
                    V1ServiceList.class,
                    "",
                    "v1",
                    "services",
                    client);

            serviceInformer = informerFactory.sharedIndexInformerFor(serviceClient, V1Service.class, 10000);
        }

        serviceInformer.addEventHandler(new ResourceEventHandler<V1Service>() {
            @Override
            public void onAdd(V1Service svc) {
                if (!hasGuacamoleAnnotations(svc)) {
                    return;
                }
                try {
                    String key = Utils.objectKey(svc);
                    connections.put(key, Utils.connectionAdapter(svc, coreV1Api));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUpdate(V1Service oldSvc, V1Service newSvc) {
                if (!hasGuacamoleAnnotations(newSvc)) {
                    String key = Utils.objectKey(newSvc);
                    connections.remove(key);
                    return;
                }
                try {
                    String key = Utils.objectKey(newSvc);
                    connections.put(key, Utils.connectionAdapter(newSvc, coreV1Api));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDelete(V1Service svc, boolean deletedFinalStateUnknown) {
                String key = Utils.objectKey(svc);
                connections.remove(key);
            }
        });
    }

    public void stop() {
        if (informerFactory != null) {
            informerFactory.stopAllRegisteredInformers();
        }
    }

    public Map<String, Connection> getConnections() {
        return connections;
    }
}
