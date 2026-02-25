package fr.corentin.guacamole.kubernetes.auth;

import static fr.corentin.guacamole.kubernetes.auth.AuthenticationProvider.injector;

import fr.corentin.guacamole.kubernetes.ConfigurationService;
import fr.corentin.guacamole.kubernetes.discovery.MachineDiscovery;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.auth.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserContext extends DelegatingUserContext {

	private final ConnectionDirectory connectionDirectory;
	private static MachineDiscovery machineDiscovery;

	private static final Logger logger = LoggerFactory.getLogger(UserContext.class);

	public static final ConcurrentMap<String, String> tunnelHostnames = new ConcurrentHashMap<>();

	public UserContext(org.apache.guacamole.net.auth.UserContext userContext, MachineDiscovery machineDiscovery) throws GuacamoleException {
		super(userContext);
		UserContext.machineDiscovery = machineDiscovery;
		this.connectionDirectory = new ConnectionDirectory(super.getConnectionDirectory(), machineDiscovery);
	}

	@Override
	public Directory<Connection> getConnectionDirectory() throws GuacamoleException {
		return this.connectionDirectory;
	}

	@Override
	public org.apache.guacamole.net.auth.ConnectionGroup getRootConnectionGroup() throws GuacamoleException {
		return new ConnectionGroup(super.getRootConnectionGroup(), machineDiscovery);
	}
}
