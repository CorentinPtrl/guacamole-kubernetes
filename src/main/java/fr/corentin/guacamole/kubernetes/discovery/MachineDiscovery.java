package fr.corentin.guacamole.kubernetes.discovery;

import com.google.inject.Inject;
import fr.corentin.guacamole.kubernetes.ConfigurationService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.Connection;
import org.apache.guacamole.net.auth.simple.SimpleConnection;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MachineDiscovery {

    @Inject
    private ConfigurationService confService;
    private ApiClient client;
    private CoreV1Api coreV1Api;
    private SharedInformerFactory informerFactory;
    private final Map<String, Connection> connections;

    private static final String PROTOCOL_ANNOTATION = "org.apache.guacamole/protocol";
    private static final String ANNOTATIONS_PREFIX = "org.apache.guacamole/";
    private static final Logger logger = LoggerFactory.getLogger(MachineDiscovery.class);

    public MachineDiscovery() {
        connections = new ConcurrentHashMap<>();
    }

    private boolean hasGuacamoleAnnotations(V1Service svc) {
        return svc.getMetadata() != null
                && svc.getMetadata().getAnnotations() != null
                && svc.getMetadata().getAnnotations().containsKey(PROTOCOL_ANNOTATION);
    }

    private String serviceKey(V1Service svc) {
        String hostname = String.format("%s.%s", svc.getMetadata().getName(), svc.getMetadata().getNamespace());
        return Integer.toString(hostname.hashCode());
    }

    private SimpleConnection connectionAdapter(V1Service svc) throws GuacamoleException {
        Map<String, String> annotations = svc.getMetadata().getAnnotations();
        String hostname = String.format("%s.%s", svc.getMetadata().getName(), svc.getMetadata().getNamespace());
        String protocol = annotations.get(PROTOCOL_ANNOTATION);

        V1ServicePort svcPort = svc.getSpec().getPorts().stream()
                .filter(port -> protocol.equals(port.getName()))
                .findFirst()
                .orElseThrow(() -> new GuacamoleException(
                        String.format("Service %s does not have a port named %s", hostname, protocol)));

        GuacamoleConfiguration guacConf = new GuacamoleConfiguration();
        guacConf.setProtocol(protocol);
        guacConf.setParameter("hostname", hostname);
        guacConf.setParameter("port", svcPort.getPort().toString());
        guacConf.setParameter("ignore-cert", "true");

        for (Map.Entry<String, String> entry : annotations.entrySet()) {
			if (!entry.getKey().startsWith(ANNOTATIONS_PREFIX) || entry.getKey().equals(PROTOCOL_ANNOTATION)) {
				continue;
			}
            String paramName = entry.getKey().substring(ANNOTATIONS_PREFIX.length());
            if (paramName.endsWith("-secret")) {
                paramName = paramName.substring(0, paramName.length()  - "-secret".length());
				String secretValue = getSecretValue(entry.getValue(), svc.getMetadata().getNamespace());
				guacConf.setParameter(paramName, secretValue);
			} else {
                guacConf.setParameter(paramName, entry.getValue());
            }
		}

        if (guacConf.getParameter("name") == null) {
			guacConf.setParameter("name", hostname);
		}

        SimpleConnection conn = new SimpleConnection(guacConf.getParameter("name"), Integer.toString(hostname.hashCode()), guacConf);
        conn.setParentIdentifier("ROOT");
        return conn;
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

        GenericKubernetesApi serviceClient = new GenericKubernetesApi<>(
                V1Service.class,
                V1ServiceList.class,
                "",
                "v1",
                "services",
                client);

        SharedIndexInformer<V1Service> serviceInformer = informerFactory.sharedIndexInformerFor(serviceClient, V1Service.class, 10000);

        serviceInformer.addEventHandler(new ResourceEventHandler<V1Service>() {
            @Override
            public void onAdd(V1Service svc) {
                if (!hasGuacamoleAnnotations(svc)) {
                    System.out.println("Service " + svc.getMetadata().getName() + " does not have Guacamole annotations, skipping.");
                    return;
                }
                try {
                    System.out.println("Adding service " + svc.getMetadata().getName() + " as a Guacamole connection.");
                    String key = serviceKey(svc);
                    connections.put(key, connectionAdapter(svc));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUpdate(V1Service oldSvc, V1Service newSvc) {
                if (!hasGuacamoleAnnotations(newSvc)) {
                    String key = serviceKey(newSvc);
                    connections.remove(key);
                    return;
                }
                try {
                    String key = serviceKey(newSvc);
                    connections.put(key, connectionAdapter(newSvc));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDelete(V1Service svc, boolean deletedFinalStateUnknown) {
                String key = serviceKey(svc);
                connections.remove(key);
            }
        });

        informerFactory.startAllRegisteredInformers();
        System.out.println("Informers started, watching for services in all namespaces");
    }

    public void stop() {
        if (informerFactory != null) {
            informerFactory.stopAllRegisteredInformers();
        }
    }

    private String getSecretValue(String secretPath, String namespace) {
        try {
            String secretName = secretPath.split("/")[0];
            String secretKey = secretPath.split("/")[1];
            V1Secret secret = coreV1Api.readNamespacedSecret(secretName, namespace, null);
            if (secret.getData() != null && secret.getData().containsKey(secretKey)) {
                return new String(secret.getData().get(secretKey));
            } else {
                logger.warn("Secret " + secretPath + " does not contain a '" + secretKey + "' key or has no data.");
            }
        } catch (ApiException e) {
            logger.error("Failed to read secret " + secretPath + " from namespace " + namespace +
                    ". HTTP status: " + e.getCode() + ", message: " + e.getMessage(), e);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Invalid secret path format: " + secretPath + ". Expected format is 'secretName/secretKey'.", e);
        }
        return "";
    }

    public Map<String, Connection> getConnections() {
        return connections;
    }
}
