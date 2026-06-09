package com.wsst;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

import com.wsst.ws_rest.WS_BrokerIntermediario;
import com.wsst.ws_rest.WS_ConsultaProsa;
import com.wsst.ws_rest.WS_Version;

@Component
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
		register(WS_Version.class);
		register(WS_BrokerIntermediario.class);
		register(WS_ConsultaProsa.class);
	}
}
