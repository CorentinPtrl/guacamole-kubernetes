package fr.corentin.guacamole.kubernetes.auth;

import fr.corentin.guacamole.kubernetes.discovery.MachineDiscovery;
import java.util.HashSet;
import java.util.Set;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.DelegatingConnectionGroup;

public class ConnectionGroup extends DelegatingConnectionGroup {

	private final MachineDiscovery machineDiscovery;

	public ConnectionGroup(org.apache.guacamole.net.auth.ConnectionGroup connectionGroup, MachineDiscovery machineDiscovery) {
		super(connectionGroup);
		this.machineDiscovery = machineDiscovery;
	}

	@Override
	public Set<String> getConnectionIdentifiers() throws GuacamoleException {
		if (super.getParentIdentifier() != null) {
			return super.getConnectionIdentifiers();
		}
		Set<String> identifiers = new HashSet<>(super.getConnectionIdentifiers());
		try {
			identifiers.addAll(machineDiscovery.getConnections().keySet());
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(identifiers);
		return identifiers;
	}
}
