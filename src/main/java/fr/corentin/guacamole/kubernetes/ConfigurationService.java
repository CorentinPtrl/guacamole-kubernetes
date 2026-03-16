package fr.corentin.guacamole.kubernetes;

import com.google.inject.Inject;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.properties.BooleanGuacamoleProperty;
import org.apache.guacamole.properties.StringGuacamoleProperty;

public class ConfigurationService {

	@Inject
	private Environment environment;

	private static final BooleanGuacamoleProperty KUBERNETES_DELEGATE_CONNECTIONS = new BooleanGuacamoleProperty() {
		@Override
		public String getName() {
			return "kubernetes-delegate-connections";
		}
	};

	private static final StringGuacamoleProperty KUBERNETES_SELECTED_NAMESPACE = new StringGuacamoleProperty() {
		@Override
		public String getName() {
			return "kubernetes-selected-namespace";
		}
	};

	public boolean shouldDelegateConnections() throws GuacamoleException {
		return environment.getProperty(KUBERNETES_DELEGATE_CONNECTIONS, false);
	}

	public String getSelectedNamespace() throws GuacamoleException {
		return environment.getProperty(KUBERNETES_SELECTED_NAMESPACE, "");
	}
}
