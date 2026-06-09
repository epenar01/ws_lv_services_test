package com.wsst.memory;

import com.wsst.beans.PropertiesForWsServices;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

public class Singleton {

	private static volatile Singleton instance;

	private String version = "1.0.0";

	private PropertiesForWsServices propertiesForFoliosOffline = new PropertiesForWsServices();

	private volatile Client httpClientBroker = null;

	private volatile Client httpClientProsaConsulta = null;

	public static Singleton getInstance() {

		Singleton result = instance;

		if (result != null) {
			return result;
		}

		synchronized (Singleton.class) {

			if (instance == null) {
				instance = new Singleton();
			}

			return instance;
		}
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public PropertiesForWsServices getPropertiesForFoliosOffline() {
		return propertiesForFoliosOffline;
	}

	public void setPropertiesForFoliosOffline(PropertiesForWsServices propertiesForFoliosOffline) {
		this.propertiesForFoliosOffline = propertiesForFoliosOffline;
	}

	public Client getHttpClientBroker() {
		Client local = httpClientBroker;
		if (local != null) {
			return local;
		}
		synchronized (Singleton.class) {
			if (httpClientBroker == null) {
				httpClientBroker = ClientBuilder.newClient()
						.property("jersey.config.client.connectTimeout",
								propertiesForFoliosOffline.getBroker_destino_timeout_connect_ms())
						.property("jersey.config.client.readTimeout",
								propertiesForFoliosOffline.getBroker_destino_timeout_read_ms());
			}
			return httpClientBroker;
		}
	}

	/**
	 * Cliente HTTP saliente dedicado a la API B24 (Consulta Transacciones Prosa).
	 * Se cachea una sola vez con sus propios timeouts; es thread-safe.
	 */
	public Client getHttpClientProsaConsulta() {
		Client local = httpClientProsaConsulta;
		if (local != null) {
			return local;
		}
		synchronized (Singleton.class) {
			if (httpClientProsaConsulta == null) {
				httpClientProsaConsulta = ClientBuilder.newClient()
						.property("jersey.config.client.connectTimeout",
								propertiesForFoliosOffline.getProsa_consulta_timeout_connect_ms())
						.property("jersey.config.client.readTimeout",
								propertiesForFoliosOffline.getProsa_consulta_timeout_read_ms());
			}
			return httpClientProsaConsulta;
		}
	}

}
