package fr.corentin.guacamole.kubernetes.auth;

import fr.corentin.guacamole.kubernetes.discovery.MachineDiscovery;
import java.util.*;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.Connection;
import org.apache.guacamole.net.auth.DelegatingDirectory;
import org.apache.guacamole.net.auth.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionDirectory extends DelegatingDirectory<Connection> {

	private final MachineDiscovery machineDiscovery;

	private static final Logger logger = LoggerFactory.getLogger(ConnectionDirectory.class);

	public ConnectionDirectory(Directory<Connection> directory, MachineDiscovery machineDiscovery) {
		super(directory);
		this.machineDiscovery = machineDiscovery;
	}

	@Override
	public void add(Connection connection) throws GuacamoleException {
		super.add(connection);
	}

	@Override
	public Connection get(String identifier) throws GuacamoleException {
		if (!machineDiscovery.getConnections().containsKey(identifier))
			return super.get(identifier);
		return machineDiscovery.getConnections().get(identifier);
	}

	@Override
	public Collection<Connection> getAll(Collection<String> identifiers) throws GuacamoleException {
		Collection<Connection> allConnections = new ArrayList<>();
		for (String identifier : identifiers) {
			if (!machineDiscovery.getConnections().containsKey(identifier))
				allConnections.add(super.get(identifier));
			else
				allConnections.add(machineDiscovery.getConnections().get(identifier));
		}

		return allConnections;
	}

	@Override
	public Set<String> getIdentifiers() throws GuacamoleException {
		Set<String> allIds = new HashSet<>(super.getIdentifiers());
		allIds.addAll(machineDiscovery.getConnections().keySet());
		return allIds;
	}
}
