package fr.corentin.guacamole.kubernetes.auth;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import fr.corentin.guacamole.kubernetes.ConfigurationService;
import fr.corentin.guacamole.kubernetes.KubernetesModule;
import fr.corentin.guacamole.kubernetes.discovery.MachineDiscovery;
import java.util.HashMap;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.simple.SimpleUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationProvider extends AbstractAuthenticationProvider {

	@Inject
	private ConfigurationService confService;

	@Inject
	private MachineDiscovery machineDiscovery;

	public static Injector injector;

	private static final Logger logger = LoggerFactory.getLogger(AuthenticationProvider.class);

	public AuthenticationProvider() {
		injector = Guice.createInjector(new KubernetesModule(this, new MachineDiscovery()));
		try {
			machineDiscovery.start();
		} catch (GuacamoleException e) {
			logger.error("Failed to initialize machine discovery due to ", e);
		}
	}

	@Override
	public org.apache.guacamole.net.auth.UserContext decorate(org.apache.guacamole.net.auth.UserContext context, AuthenticatedUser authenticatedUser, Credentials credentials)
			throws GuacamoleException {
		if (!confService.shouldDelegateConnections()) {
			return null;
		}
		if (context instanceof UserContext) {
			return context;
		}
		return new UserContext(context, machineDiscovery);
	}

	@Override
	public org.apache.guacamole.net.auth.UserContext redecorate(org.apache.guacamole.net.auth.UserContext decorated, org.apache.guacamole.net.auth.UserContext context, AuthenticatedUser authenticatedUser, Credentials credentials) throws GuacamoleException {
		if (!confService.shouldDelegateConnections()) {
			return null;
		}
		if (context instanceof UserContext) {
			return context;
		} else if (decorated instanceof UserContext) {
			return decorated;
		} else {
			return new UserContext(context, machineDiscovery);
		}
	}

	@Override
	public void shutdown() {
		machineDiscovery.stop();
	}

	@Override
	public org.apache.guacamole.net.auth.UserContext getUserContext(AuthenticatedUser authenticatedUser) throws GuacamoleException {
		if (confService.shouldDelegateConnections()) {
			return null;
		}
		return new UserContext(new SimpleUserContext(this, new HashMap<>()), machineDiscovery);
	}

	@Override
	public String getIdentifier() {
		return "kubernetes";
	}
}
