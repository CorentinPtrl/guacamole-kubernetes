# Guacamole Kubernetes Extension

An [Apache Guacamole](https://guacamole.apache.org/) extension that automatically discovers Kubernetes services and exposes them as Guacamole connections. Just annotate your services — no manual Guacamole configuration needed.

## Quick Start

### Build

```bash
./gradlew shadowJar
```

### Install

Copy `guacamole-kubernetes-1.6.0-all.jar` to `/etc/guacamole/extensions/` and restart Guacamole.

### Annotate a Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-desktop
  annotations:
    org.apache.guacamole/protocol: "rdp"
    org.apache.guacamole/name: "My Desktop"
    org.apache.guacamole/username: "admin"
    org.apache.guacamole/password-secret: "my-secret/password"
spec:
  ports:
    - name: rdp          # must match the protocol annotation
      port: 3389
  selector:
    app: my-desktop
```

The connection appears in Guacamole automatically. `hostname` and `port` are derived from the service — no need to set them.

## Annotations

Any annotation prefixed with `org.apache.guacamole/` is forwarded as a [Guacamole connection parameter](https://guacamole.apache.org/doc/gug/configuring-guacamole.html#connection-configuration).

| Annotation | Description |
|---|---|
| `org.apache.guacamole/protocol` | **(required)** Connection protocol: `rdp`, `vnc`, `ssh`, `telnet`, `kubernetes` |
| `org.apache.guacamole/name` | Display name (defaults to `<service>.<namespace>`) |
| `org.apache.guacamole/<param>` | Any connection parameter (e.g. `username`, `security`, `color-depth`) |
| `org.apache.guacamole/<param>-secret` | Read the parameter value from a Kubernetes Secret (`<secretName>/<key>`) |

### Secret References

Annotations ending in `-secret` resolve their value from a Kubernetes Secret in the same namespace. The format is `<secretName>/<key>`:

```yaml
org.apache.guacamole/password-secret: "my-secret/password"
#                                      ─────────  ────────
#                                      secret name    key
```

## Configuration

Optional property in `guacamole.properties`:

| Property | Default | Description |
|---|---|---|
| `kubernetes-delegate-connections` | `false` | Merge discovered connections into another auth provider's directory instead of providing a standalone context |

## Requirements

- Apache Guacamole **1.6.0**
- Java 8+
- Kubernetes cluster with a service account that can read **Services** and **Secrets**

## License

[Apache License 2.0](LICENSE)
