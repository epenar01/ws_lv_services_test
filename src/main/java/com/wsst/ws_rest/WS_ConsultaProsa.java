package com.wsst.ws_rest;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsst.beans.PropertiesForWsServices;
import com.wsst.beans.RespDefault;
import com.wsst.memory.Singleton;
import com.wsst.utils.Utilities;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Consulta de transacciones de debito Prosa contra la API Base24 (B24).
 *
 * Flujo (forma alterna del diagrama):
 *   POS  ->  ws_services_test  ->  API B24  ->  Key Monitor
 *
 *   1. El POS hace POST a /rest/v1/return/ConsultaTransacciones con el JSON
 *      de consulta: { fecha, tienda, terminal, boleta, empresa }.
 *   2. Este servicio reenvia ese JSON TAL CUAL a la API B24
 *      (URL configurada en properties_wsst.properties), inyectando el header
 *      Authorization: Basic con las credenciales de configuracion.
 *   3. B24 responde con su propio JSON (codRespuesta, descRespuesta,
 *      Transaccion[]).
 *   4. Este servicio devuelve al POS el body de B24 TAL CUAL, con el mismo
 *      HTTP status (passthrough completo).
 *
 * El proposito actual es MONITOREO: los logs detallan cada transaccion.
 * El PAN (campo "tarjeta") se ve completo o enmascarado segun el flag
 * wsst.prosa.consulta.log.mask.pan. La password NUNCA se escribe en logs.
 */
@Path("/v1/return")
public class WS_ConsultaProsa {

	private Logger logger = Logger.getLogger(WS_ConsultaProsa.class);

	/** ObjectMapper es thread-safe; se reutiliza para parsear el resumen de logs. */
	private static final ObjectMapper JSON = new ObjectMapper();

	@POST
	@Path("/ConsultaTransacciones")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response fnConsultaTransacciones(
			String jsonOrigen,
			@Context HttpServletRequest httpReq,
			@Context HttpHeaders headers) {

		// Correlation id para trazar el ciclo completo de este request en los logs.
		String reqId = UUID.randomUUID().toString().substring(0, 8);
		long t0 = System.currentTimeMillis();

		String remoteIp = httpReq != null ? httpReq.getRemoteAddr() : "desconocido";
		String remotePort = httpReq != null ? String.valueOf(httpReq.getRemotePort()) : "?";
		String userAgent = headers != null ? headers.getHeaderString("User-Agent") : null;
		String contentType = headers != null ? headers.getHeaderString("Content-Type") : null;

		logger.info("=====================================================");
		logger.info("[" + reqId + "] CONSULTA-PROSA - REQUEST recibido del POS (ORIGEN)");
		logger.info("[" + reqId + "] ORIGEN IP: " + remoteIp + ":" + remotePort);
		logger.info("[" + reqId + "] User-Agent: " + userAgent);
		logger.info("[" + reqId + "] Content-Type: " + contentType);
		logger.info("[" + reqId + "] Body de consulta recibido: " + (jsonOrigen == null ? "(null)"
				: Utilities.fnQuitarSaltosTab(jsonOrigen)));

		RespDefault respError = new RespDefault();

		// 1. Validacion de entrada
		if (jsonOrigen == null || jsonOrigen.trim().length() == 0) {
			respError.setCode("-1");
			respError.setMessage("Body vacio: el origen no envio payload de consulta");
			logger.warn("[" + reqId + "] CONSULTA-PROSA - RECHAZADO: body vacio -> HTTP 400");
			logger.info("[" + reqId + "] Duracion: " + (System.currentTimeMillis() - t0) + "ms");
			return Response.status(Response.Status.BAD_REQUEST).entity(respError).build();
		}

		Singleton singleton = Singleton.getInstance();
		PropertiesForWsServices props = singleton.getPropertiesForFoliosOffline();
		String urlDestino = props.getProsa_consulta_url();
		boolean maskPan = props.isProsa_consulta_log_mask_pan();

		// 2. Validacion de configuracion
		if (urlDestino == null || urlDestino.trim().length() == 0) {
			respError.setCode("-2");
			respError.setMessage("Configuracion invalida: wsst.prosa.consulta.url no esta definida");
			logger.error("[" + reqId + "] CONSULTA-PROSA - CONFIG ERROR: URL de B24 no definida -> HTTP 500");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(respError).build();
		}

		String authUser = props.getProsa_consulta_auth_user();
		String authPass = props.getProsa_consulta_auth_password();
		if (authUser == null || authUser.length() == 0 || authPass == null) {
			respError.setCode("-2");
			respError.setMessage("Configuracion invalida: credenciales de B24 no definidas");
			logger.error("[" + reqId + "] CONSULTA-PROSA - CONFIG ERROR: credenciales B24 no definidas -> HTTP 500");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(respError).build();
		}

		// Header Basic. Se construye en memoria; el token NUNCA se loguea.
		String basicToken = "Basic " + Base64.getEncoder()
				.encodeToString((authUser + ":" + authPass).getBytes(StandardCharsets.UTF_8));

		logger.info("[" + reqId + "] CONSULTA-PROSA - REENVIANDO a la API B24 (DESTINO)");
		logger.info("[" + reqId + "] DESTINO URL: " + urlDestino);
		logger.info("[" + reqId + "] Autorizacion: Basic (usuario=" + Utilities.fnMaskUser(authUser) + ", password=***)");
		logger.info("[" + reqId + "] Body enviado a B24: " + Utilities.fnQuitarSaltosTab(jsonOrigen));

		// 3. Reenvio a B24 con el header Authorization inyectado
		Response respuestaDestino = null;
		String bodyDestino = null;
		int statusDestino;
		long tDestinoStart = System.currentTimeMillis();
		try {
			Client client = singleton.getHttpClientProsaConsulta();
			WebTarget target = client.target(urlDestino);
			Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON)
					.header(HttpHeaders.AUTHORIZATION, basicToken);
			respuestaDestino = builder.post(Entity.entity(jsonOrigen, MediaType.APPLICATION_JSON));
			statusDestino = respuestaDestino.getStatus();
			bodyDestino = respuestaDestino.readEntity(String.class);

			long tDestino = System.currentTimeMillis() - tDestinoStart;
			logger.info("[" + reqId + "] CONSULTA-PROSA - B24 respondio en " + tDestino + "ms");
			logger.info("[" + reqId + "] B24 status: " + statusDestino);
			logger.info("[" + reqId + "] B24 body: " + fnBodyParaLog(bodyDestino, maskPan));

			// Resumen legible, transaccion por transaccion (proposito: monitoreo).
			fnLogResumenTransacciones(reqId, bodyDestino, maskPan);

		} catch (ProcessingException pex) {
			Throwable causa = pex.getCause();
			if (causa instanceof SocketTimeoutException) {
				logger.error("[" + reqId + "] CONSULTA-PROSA - TIMEOUT llamando a B24 [" + urlDestino + "]", pex);
				respError.setCode("-3");
				respError.setMessage("Timeout al contactar la API B24");
				logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
				return Response.status(Response.Status.GATEWAY_TIMEOUT).entity(respError).build();
			}
			logger.error("[" + reqId + "] CONSULTA-PROSA - ERROR de transporte llamando a B24 [" + urlDestino + "]", pex);
			respError.setCode("-4");
			respError.setMessage("No se pudo contactar la API B24: " + pex.getMessage());
			logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
			return Response.status(Response.Status.BAD_GATEWAY).entity(respError).build();
		} catch (Exception ex) {
			logger.error("[" + reqId + "] CONSULTA-PROSA - ERROR inesperado llamando a B24", ex);
			respError.setCode("-5");
			respError.setMessage("Error inesperado: " + ex.getMessage());
			logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(respError).build();
		} finally {
			if (respuestaDestino != null) {
				respuestaDestino.close();
			}
		}

		// 4. Passthrough: devolvemos al POS el status y body de B24 TAL CUAL.
		String bodyOut = bodyDestino == null ? "" : bodyDestino;
		logger.info("[" + reqId + "] CONSULTA-PROSA - RESPONDIENDO al POS (ORIGEN)");
		logger.info("[" + reqId + "] Status enviado al POS: " + statusDestino);
		logger.info("[" + reqId + "] Body enviado al POS: " + fnBodyParaLog(bodyOut, maskPan));
		logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
		logger.info("=====================================================");

		return Response.status(statusDestino)
				.entity(bodyOut)
				.type(MediaType.APPLICATION_JSON)
				.build();
	}

	/**
	 * Prepara un body para escribirlo en el log: quita saltos de linea y, si el
	 * enmascarado esta activo, oculta el PAN del campo "tarjeta".
	 */
	private String fnBodyParaLog(String body, boolean maskPan) {
		if (body == null) {
			return "(vacio)";
		}
		String limpio = Utilities.fnQuitarSaltosTab(body);
		return maskPan ? Utilities.fnMaskPanInJson(limpio) : limpio;
	}

	/**
	 * Escribe en el log un resumen legible de la respuesta de B24: codigo,
	 * descripcion y una linea por cada transaccion encontrada. Defensivo: si el
	 * body no es el JSON esperado, solo lo anota y nunca rompe el passthrough.
	 */
	private void fnLogResumenTransacciones(String reqId, String bodyDestino, boolean maskPan) {
		if (bodyDestino == null || bodyDestino.trim().length() == 0) {
			logger.warn("[" + reqId + "] CONSULTA-PROSA - B24 devolvio body vacio, no hay transacciones que detallar");
			return;
		}
		try {
			JsonNode root = JSON.readTree(bodyDestino);
			String cod = root.path("codRespuesta").asText("(sin codRespuesta)");
			String desc = root.path("descRespuesta").asText("(sin descRespuesta)");
			logger.info("[" + reqId + "] CONSULTA-PROSA - Resultado: codRespuesta=" + cod + " descRespuesta=" + desc);

			JsonNode txs = root.path("Transaccion");
			if (!txs.isArray() || txs.size() == 0) {
				logger.info("[" + reqId + "] CONSULTA-PROSA - Sin transacciones en la respuesta");
				return;
			}
			logger.info("[" + reqId + "] CONSULTA-PROSA - Transacciones encontradas: " + txs.size());
			int i = 1;
			for (JsonNode tx : txs) {
				String autorizacion = tx.path("autorizacion").asText("");
				String monto = tx.path("monto").asText("");
				String rrn = tx.path("rrn").asText("");
				String tarjeta = tx.path("tarjeta").asText("");
				String tarjetaLog = maskPan ? Utilities.fnMaskPan(tarjeta) : tarjeta;
				logger.info("[" + reqId + "] CONSULTA-PROSA - Tx#" + i + " | autorizacion=" + autorizacion
						+ " | monto=" + monto + " | rrn=" + rrn + " | tarjeta=" + tarjetaLog);
				i++;
			}
		} catch (Exception ex) {
			logger.warn("[" + reqId + "] CONSULTA-PROSA - No se pudo parsear la respuesta de B24 para el resumen: "
					+ ex.getMessage());
		}
	}

}
