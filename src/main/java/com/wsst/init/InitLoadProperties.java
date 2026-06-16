package com.wsst.init;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import jakarta.annotation.PostConstruct;

import org.jboss.logging.Logger;
import org.springframework.stereotype.Component;

import com.wsst.beans.PropertiesForWsServices;
import com.wsst.memory.Singleton;
import com.wsst.utils.Utilities;

@Component
public class InitLoadProperties {

	private static final Logger logger = Logger.getLogger(InitLoadProperties.class);

	private static final String RESOURCE_NAME = "properties_wsst.properties";

	@PostConstruct
	public void fnLoadProperties() {

		Singleton sg = Singleton.getInstance();

		try (InputStream inputSt = getClass().getClassLoader().getResourceAsStream(RESOURCE_NAME)) {

			if (inputSt == null) {
				logger.warn("No se encontro " + RESOURCE_NAME + " en el classpath. Se usan defaults.");
				return;
			}

			Properties propert = new Properties();
			propert.load(inputSt);

			String brokerUrl = propert.getProperty("wsst.broker.destino.url");
			if (brokerUrl != null && brokerUrl.trim().length() > 0) {
				sg.getPropertiesForFoliosOffline().setBroker_destino_url(brokerUrl.trim());
			}
			logger.info("broker.destino.url:" + sg.getPropertiesForFoliosOffline().getBroker_destino_url());

			String connectMs = propert.getProperty("wsst.broker.destino.timeout.connect.ms");
			if (connectMs != null && connectMs.trim().length() > 0) {
				try {
					sg.getPropertiesForFoliosOffline()
							.setBroker_destino_timeout_connect_ms(Integer.parseInt(connectMs.trim()));
				} catch (NumberFormatException nfex) {
					logger.warn("wsst.broker.destino.timeout.connect.ms invalido, se queda en default: " + connectMs);
				}
			}

			String readMs = propert.getProperty("wsst.broker.destino.timeout.read.ms");
			if (readMs != null && readMs.trim().length() > 0) {
				try {
					sg.getPropertiesForFoliosOffline()
							.setBroker_destino_timeout_read_ms(Integer.parseInt(readMs.trim()));
				} catch (NumberFormatException nfex) {
					logger.warn("wsst.broker.destino.timeout.read.ms invalido, se queda en default: " + readMs);
				}
			}

			// ===== Consulta Transacciones Debito Prosa (API Base24) =====
			PropertiesForWsServices p = sg.getPropertiesForFoliosOffline();

			String prosaUrl = propert.getProperty("wsst.prosa.consulta.url");
			if (prosaUrl != null && prosaUrl.trim().length() > 0) {
				p.setProsa_consulta_url(prosaUrl.trim());
			}
			logger.info("prosa.consulta.url:" + p.getProsa_consulta_url());

			String prosaUser = propert.getProperty("wsst.prosa.consulta.auth.user");
			if (prosaUser != null && prosaUser.trim().length() > 0) {
				p.setProsa_consulta_auth_user(prosaUser.trim());
			}
			// El usuario se loguea enmascarado; la password NUNCA se escribe en el log.
			logger.info("prosa.consulta.auth.user:" + Utilities.fnMaskUser(p.getProsa_consulta_auth_user()));

			String prosaPass = propert.getProperty("wsst.prosa.consulta.auth.password");
			if (prosaPass != null && prosaPass.length() > 0) {
				p.setProsa_consulta_auth_password(prosaPass);
			}
			logger.info("prosa.consulta.auth.password:" + (p.getProsa_consulta_auth_password() == null
					|| p.getProsa_consulta_auth_password().isEmpty() ? "(no definida)" : "(definida)"));

			String prosaConnectMs = propert.getProperty("wsst.prosa.consulta.timeout.connect.ms");
			if (prosaConnectMs != null && prosaConnectMs.trim().length() > 0) {
				try {
					p.setProsa_consulta_timeout_connect_ms(Integer.parseInt(prosaConnectMs.trim()));
				} catch (NumberFormatException nfex) {
					logger.warn("wsst.prosa.consulta.timeout.connect.ms invalido, se queda en default: " + prosaConnectMs);
				}
			}

			String prosaReadMs = propert.getProperty("wsst.prosa.consulta.timeout.read.ms");
			if (prosaReadMs != null && prosaReadMs.trim().length() > 0) {
				try {
					p.setProsa_consulta_timeout_read_ms(Integer.parseInt(prosaReadMs.trim()));
				} catch (NumberFormatException nfex) {
					logger.warn("wsst.prosa.consulta.timeout.read.ms invalido, se queda en default: " + prosaReadMs);
				}
			}

			String prosaMaskPan = propert.getProperty("wsst.prosa.consulta.log.mask.pan");
			if (prosaMaskPan != null && prosaMaskPan.trim().length() > 0) {
				p.setProsa_consulta_log_mask_pan(Boolean.parseBoolean(prosaMaskPan.trim()));
			}
			logger.info("prosa.consulta.log.mask.pan:" + p.isProsa_consulta_log_mask_pan()
					+ (p.isProsa_consulta_log_mask_pan() ? " (PAN enmascarado)" : " (PAN visible - monitoreo)"));

			logger.info("WS Version:" + sg.getVersion());

		} catch (IOException ex) {
			logger.error("Error leyendo " + RESOURCE_NAME, ex);
		}
	}
}
