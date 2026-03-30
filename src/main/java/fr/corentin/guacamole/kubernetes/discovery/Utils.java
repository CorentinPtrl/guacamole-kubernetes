package fr.corentin.guacamole.kubernetes.discovery;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.proto.V1;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.simple.SimpleConnection;
import org.apache.guacamole.protocol.GuacamoleConfiguration;

import java.util.Map;

import static fr.corentin.guacamole.kubernetes.discovery.MachineDiscovery.*;

public class Utils {

    static SimpleConnection connectionAdapter(V1Service svc, CoreV1Api coreV1Api) throws GuacamoleException {
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
                String secretValue = getSecretValue(entry.getValue(), svc.getMetadata().getNamespace(), coreV1Api);
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

    static SimpleConnection connectionAdapter(V1Pod pod, CoreV1Api coreV1Api) throws GuacamoleException {
        Map<String, String> annotations = pod.getMetadata().getAnnotations();
        String protocol = annotations.get(PROTOCOL_ANNOTATION);

        V1ContainerPort podPort;

        podPort = pod.getSpec().getContainers().stream()
                .flatMap(container -> container.getPorts() != null ? container.getPorts().stream() : null)
                .filter(port -> protocol.equals(port.getName()))
                .findFirst()
                .orElseThrow(() -> new GuacamoleException(
                        String.format("Pod %s does not have a container port named %s", pod.getMetadata().getName(), protocol)));

        GuacamoleConfiguration guacConf = new GuacamoleConfiguration();
        guacConf.setProtocol(protocol);
        guacConf.setParameter("hostname", pod.getStatus().getPodIP());
        guacConf.setParameter("port", podPort.getContainerPort().toString());
        guacConf.setParameter("ignore-cert", "true");

        for (Map.Entry<String, String> entry : annotations.entrySet()) {
            if (!entry.getKey().startsWith(ANNOTATIONS_PREFIX) || entry.getKey().equals(PROTOCOL_ANNOTATION)) {
                continue;
            }
            String paramName = entry.getKey().substring(ANNOTATIONS_PREFIX.length());
            if (paramName.endsWith("-secret")) {
                paramName = paramName.substring(0, paramName.length()  - "-secret".length());
                String secretValue = getSecretValue(entry.getValue(), pod.getMetadata().getNamespace(), coreV1Api);
                guacConf.setParameter(paramName, secretValue);
            } else {
                guacConf.setParameter(paramName, entry.getValue());
            }
        }

        if (guacConf.getParameter("name") == null) {
            guacConf.setParameter("name", pod.getMetadata().getName());
        }

        SimpleConnection conn = new SimpleConnection(guacConf.getParameter("name"), pod.getStatus().getPodIP(), guacConf);
        conn.setParentIdentifier("ROOT");
        return conn;
    }

    static String getSecretValue(String secretPath, String namespace, CoreV1Api coreV1Api) {
        try {
            String secretName = secretPath.split(":")[0];
            String secretKey = secretPath.split(":")[1];
            V1Secret secret = coreV1Api.readNamespacedSecret(secretName, namespace, null);
            if (!(secret.getData() != null && secret.getData().containsKey(secretKey))){
                logger.warn("Secret " + secretPath + " does not contain a '" + secretKey + "' key or has no data.");
                return "";
            }
            return new String(secret.getData().get(secretKey));
        } catch (ApiException e) {
            logger.error("Failed to read secret " + secretPath + " from namespace " + namespace +
                    ". HTTP status: " + e.getCode() + ", message: " + e.getMessage(), e);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Invalid secret path format: " + secretPath + ". Expected format is 'secretName/secretKey'.", e);
        }
        return "";
    }

    static String serviceKey(V1Service svc) {
        String hostname = String.format("%s.%s", svc.getMetadata().getName(), svc.getMetadata().getNamespace());
        return Integer.toString(hostname.hashCode());
    }

}
