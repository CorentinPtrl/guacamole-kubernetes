package fr.corentin.guacamole.kubernetes.discovery;

import com.google.inject.Inject;
import fr.corentin.guacamole.kubernetes.ConfigurationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import fr.corentin.guacamole.kubernetes.auth.UserContext;
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
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.Config;
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
    private static final String NAME_ANNOTATION = "org.apache.guacamole/name";
    private static final String USERNAME_ANNOTATION = "org.apache.guacamole/username";
    private static final String PASSWORD_ANNOTATION = "org.apache.guacamole/password-secret";
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

    private String getPassword(String secretName) {
        try {
            V1Secret secret = coreV1Api.readNamespacedSecret(secretName, getCurrentNamespace(), null);
            if (secret.getData() != null && secret.getData().containsKey("password")) {
                return new String(Base64.getDecoder().decode(secret.getData().get("password"))).trim();
            } else {
                logger.warn("Secret " + secretName + " does not contain a 'password' key or has no data.");
            }
        } catch (ApiException e) {
            logger.error("Failed to read secret " + secretName + " from namespace " + getCurrentNamespace() +
                    ". HTTP status: " + e.getCode() + ", message: " + e.getMessage(), e);
        }
        return "";
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

        boolean hasAuth = annotations.containsKey(USERNAME_ANNOTATION) &&
                         annotations.containsKey(PASSWORD_ANNOTATION);
        if (hasAuth) {
            guacConf.setParameter("username", annotations.get(USERNAME_ANNOTATION));
            guacConf.setParameter("password", getPassword(annotations.get(PASSWORD_ANNOTATION)));
        } else {
            guacConf.setParameter("disable-auth", "true");
        }

        String connectionName = annotations.get(NAME_ANNOTATION);
        guacConf.setParameter("name", connectionName);

        SimpleConnection conn = new SimpleConnection(connectionName, Integer.toString(hostname.hashCode()), guacConf);
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

        SharedIndexInformer<V1Service> serviceInformer =
                informerFactory.sharedIndexInformerFor(
                        (CallGeneratorParams params) -> {
                            return coreV1Api.listNamespacedServiceCall(
                                    getCurrentNamespace(),
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
                } catch (GuacamoleException e) {
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
                } catch (GuacamoleException e) {
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
        System.out.println("Informers started, watching for services in namespace " + getCurrentNamespace());
    }

    public void stop() {
        if (informerFactory != null) {
            informerFactory.stopAllRegisteredInformers();
        }
    }

    public Map<String, Connection> getConnections() {
        return connections;
    }

    private static String getCurrentNamespace() {
        try {
            String namespacePath = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";
            if (Files.exists(Paths.get(namespacePath))) {
                return new String(Files.readAllBytes(Paths.get(namespacePath))).trim();
            }
        } catch (IOException e) {
        }
        return "default";
    }
}
