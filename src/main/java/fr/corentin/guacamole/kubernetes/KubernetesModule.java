package fr.corentin.guacamole.kubernetes;

import com.google.inject.AbstractModule;
import fr.corentin.guacamole.kubernetes.discovery.MachineDiscovery;
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.environment.LocalEnvironment;
import org.apache.guacamole.net.auth.AuthenticationProvider;

public class KubernetesModule extends AbstractModule {

	private final Environment environment;

	private final AuthenticationProvider authProvider;
	private final MachineDiscovery machineDiscovery;

	public KubernetesModule(AuthenticationProvider authProvider, MachineDiscovery machineDiscovery) {
		this.authProvider = authProvider;
		this.machineDiscovery = machineDiscovery;
		this.environment = new LocalEnvironment();
	}

	@Override
	protected void configure() {
		bind(AuthenticationProvider.class).toInstance(authProvider);
		bind(Environment.class).toInstance(environment);
		bind(MachineDiscovery.class).toInstance(machineDiscovery);

		bind(ConfigurationService.class);
	}
}
