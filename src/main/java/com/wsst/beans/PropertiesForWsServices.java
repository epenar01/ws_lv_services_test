package com.wsst.beans;

public class PropertiesForWsServices {

	private String broker_destino_url = null;
	private int broker_destino_timeout_connect_ms = 5000;
	private int broker_destino_timeout_read_ms = 15000;

	// ===== Consulta Transacciones Debito Prosa (API Base24) =====
	private String prosa_consulta_url = null;
	private String prosa_consulta_auth_user = null;
	private String prosa_consulta_auth_password = null;
	private int prosa_consulta_timeout_connect_ms = 5000;
	private int prosa_consulta_timeout_read_ms = 15000;
	/** false = PAN visible en logs (monitoreo); true = PAN enmascarado (PCI). */
	private boolean prosa_consulta_log_mask_pan = false;

	public String getBroker_destino_url() {
		return broker_destino_url;
	}

	public void setBroker_destino_url(String broker_destino_url) {
		this.broker_destino_url = broker_destino_url;
	}

	public int getBroker_destino_timeout_connect_ms() {
		return broker_destino_timeout_connect_ms;
	}

	public void setBroker_destino_timeout_connect_ms(int broker_destino_timeout_connect_ms) {
		this.broker_destino_timeout_connect_ms = broker_destino_timeout_connect_ms;
	}

	public int getBroker_destino_timeout_read_ms() {
		return broker_destino_timeout_read_ms;
	}

	public void setBroker_destino_timeout_read_ms(int broker_destino_timeout_read_ms) {
		this.broker_destino_timeout_read_ms = broker_destino_timeout_read_ms;
	}

	// ===== Consulta Transacciones Debito Prosa (API Base24) =====

	public String getProsa_consulta_url() {
		return prosa_consulta_url;
	}

	public void setProsa_consulta_url(String prosa_consulta_url) {
		this.prosa_consulta_url = prosa_consulta_url;
	}

	public String getProsa_consulta_auth_user() {
		return prosa_consulta_auth_user;
	}

	public void setProsa_consulta_auth_user(String prosa_consulta_auth_user) {
		this.prosa_consulta_auth_user = prosa_consulta_auth_user;
	}

	public String getProsa_consulta_auth_password() {
		return prosa_consulta_auth_password;
	}

	public void setProsa_consulta_auth_password(String prosa_consulta_auth_password) {
		this.prosa_consulta_auth_password = prosa_consulta_auth_password;
	}

	public int getProsa_consulta_timeout_connect_ms() {
		return prosa_consulta_timeout_connect_ms;
	}

	public void setProsa_consulta_timeout_connect_ms(int prosa_consulta_timeout_connect_ms) {
		this.prosa_consulta_timeout_connect_ms = prosa_consulta_timeout_connect_ms;
	}

	public int getProsa_consulta_timeout_read_ms() {
		return prosa_consulta_timeout_read_ms;
	}

	public void setProsa_consulta_timeout_read_ms(int prosa_consulta_timeout_read_ms) {
		this.prosa_consulta_timeout_read_ms = prosa_consulta_timeout_read_ms;
	}

	public boolean isProsa_consulta_log_mask_pan() {
		return prosa_consulta_log_mask_pan;
	}

	public void setProsa_consulta_log_mask_pan(boolean prosa_consulta_log_mask_pan) {
		this.prosa_consulta_log_mask_pan = prosa_consulta_log_mask_pan;
	}
}
