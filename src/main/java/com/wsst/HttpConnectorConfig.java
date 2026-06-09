package com.wsst;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpConnectorConfig {

	private static final int HTTP_PORT = 7074;

	@Bean
	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpConnector() {
		return factory -> {
			Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
			connector.setPort(HTTP_PORT);
			connector.setSecure(false);
			connector.setScheme("http");
			factory.addAdditionalTomcatConnectors(connector);
		};
	}
}
