# Guacamole Kubernetes Extension

A Kubernetes-native extension for [Apache Guacamole](https://guacamole.apache.org/) that automatically discovers and exposes services within a Kubernetes cluster as Guacamole connections.

## Overview

This extension enables Apache Guacamole to dynamically discover services running in a Kubernetes cluster and automatically create remote desktop/SSH connections to them. Services are discovered via Kubernetes Service annotations, making it easy to expose applications without manual configuration.

## Features

- **Automatic Service Discovery**: Continuously monitors Kubernetes services and automatically creates/removes Guacamole connections
- **Annotation-Based Configuration**: Simple service annotation to expose applications through Guacamole
- **In-Cluster Operation**: Designed to run as a Guacamole deployment within Kubernetes
- **Dynamic Connection Management**: Real-time updates as services are created or destroyed
- **Delegation Mode**: Optional connection delegation to work alongside other authentication providers

## Requirements

- Apache Guacamole 1.6.0
- Java 8 or higher
- Kubernetes cluster (for deployment)
- Access to Kubernetes API (via service account when running in-cluster)

### Deployment

1. Copy the JAR file to your Guacamole extensions directory:
```bash
cp build/libs/guacamole-kubernetes-1.6.0.jar /etc/guacamole/extensions/
```

2. Restart Guacamole to load the extension.

## Configuration

### Service Annotations

To expose a Kubernetes service through Guacamole, add the following annotations:

```yaml
annotations:
  org.apache.guacamole/protocol: "rdp"
  org.apache.guacamole/name: "My Desktop"
  org.apache.guacamole/username: "admin"
  org.apache.guacamole/password-secret: "my-secret"
```

### Supported Annotations

| Annotation | Description | Example |
|------------|-------------|---------|
| `org.apache.guacamole/protocol` | Connection protocol (rdp, vnc) | `rdp` |
| `org.apache.guacamole/name` | Display name in Guacamole | `My Desktop` |
| `org.apache.guacamole/username` | Username for authentication | `admin` |
| `org.apache.guacamole/password-secret` | Kubernetes Secret name containing the password | `my-secret` |
