package com.wsst.ws_rest;

import java.net.SocketTimeoutException;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.wsst.beans.PropertiesForWsServices;
import com.wsst.beans.RespDefault;
import com.wsst.memory.Singleton;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Consumes;
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
 * Broker / intermediario sincrono.
 *
 * Flujo:
 *   1. ORIGEN hace POST a /v1/mp/brk/proxy/orden con un JSON arbitrario.
 *   2. Este servicio reenvia ese JSON tal cual al sistema DESTINO
 *      (URL configurada en properties_wsst.properties).
 *   3. DESTINO responde con su propio JSON.
 *   4. Este servicio devuelve al ORIGEN el body del DESTINO TAL CUAL,
 *      con el mismo HTTP status. Es decir: passthrough completo.
 */
@Path("/v1/mp/brk/proxy")
public class WS_BrokerIntermediario {

	private Logger logger = Logger.getLogger(WS_BrokerIntermediario.class);

	@POST
	@Path("/orden")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response fnReenviarOrden(
			String jsonOrigen,
			@Context HttpServletRequest httpReq,
			@Context HttpHeaders headers) {

		// Correlation id para trazar TODO el ciclo de este request en los logs
		String reqId = UUID.randomUUID().toString().substring(0, 8);
		long t0 = System.currentTimeMillis();

		String remoteIp = httpReq != null ? httpReq.getRemoteAddr() : "desconocido";
		String remotePort = httpReq != null ? String.valueOf(httpReq.getRemotePort()) : "?";
		String userAgent = headers != null ? headers.getHeaderString("User-Agent") : null;
		String contentType = headers != null ? headers.getHeaderString("Content-Type") : null;

		logger.info("=====================================================");
		logger.info("[" + reqId + "] BROKER - REQUEST recibido del ORIGEN");
		logger.info("[" + reqId + "] ORIGEN IP: " + remoteIp + ":" + remotePort);
		logger.info("[" + reqId + "] User-Agent: " + userAgent);
		logger.info("[" + reqId + "] Content-Type: " + contentType);
		logger.info("[" + reqId + "] Body recibido: " + (jsonOrigen == null ? "(null)"
				: jsonOrigen.replaceAll("\\r|\\n", "")));

		RespDefault respError = new RespDefault();

		// 1. Validacion de entrada
		if (jsonOrigen == null || jsonOrigen.trim().length() == 0) {
			respError.setCode("-1");
			respError.setMessage("Body vacio: el origen no envio payload");
			logger.warn("[" + reqId + "] BROKER - RECHAZADO: body vacio -> HTTP 400");
			logger.info("[" + reqId + "] Duracion: " + (System.currentTimeMillis() - t0) + "ms");
			return Response.status(Response.Status.BAD_REQUEST).entity(respError).build();
		}

		Singleton singleton = Singleton.getInstance();
		PropertiesForWsServices props = singleton.getPropertiesForFoliosOffline();
		String urlDestino = props.getBroker_destino_url();

		if (urlDestino == null || urlDestino.trim().length() == 0) {
			respError.setCode("-2");
			respError.setMessage("Configuracion invalida: wsst.broker.destino.url no esta definida");
			logger.error("[" + reqId + "] BROKER - CONFIG ERROR: URL destino no definida -> HTTP 500");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(respError).build();
		}

		logger.info("[" + reqId + "] BROKER - REENVIANDO al DESTINO");
		logger.info("[" + reqId + "] DESTINO URL: " + urlDestino);
		logger.info("[" + reqId + "] Body enviado al destino: " + jsonOrigen.replaceAll("\\r|\\n", ""));

		// 2. Reenvio al destino
		Response respuestaDestino = null;
		String bodyDestino = null;
		int statusDestino;
		long tDestinoStart = System.currentTimeMillis();
		try {
			Client client = singleton.getHttpClientBroker();
			WebTarget target = client.target(urlDestino);
			Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);
			respuestaDestino = builder.post(Entity.entity(jsonOrigen, MediaType.APPLICATION_JSON));
			statusDestino = respuestaDestino.getStatus();
			bodyDestino = respuestaDestino.readEntity(String.class);

			long tDestino = System.currentTimeMillis() - tDestinoStart;
			logger.info("[" + reqId + "] BROKER - DESTINO respondio en " + tDestino + "ms");
			logger.info("[" + reqId + "] DESTINO status: " + statusDestino);
			logger.info("[" + reqId + "] DESTINO body: " + (bodyDestino == null ? "(vacio)"
					: bodyDestino.replaceAll("\\r|\\n", "")));
		} catch (ProcessingException pex) {
			Throwable causa = pex.getCause();
			if (causa instanceof SocketTimeoutException) {
				logger.error("[" + reqId + "] BROKER - TIMEOUT llamando al destino [" + urlDestino + "]", pex);
				respError.setCode("-3");
				respError.setMessage("Timeout al contactar al destino");
				logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
				return Response.status(Response.Status.GATEWAY_TIMEOUT).entity(respError).build();
			}
			logger.error("[" + reqId + "] BROKER - ERROR de transporte llamando al destino [" + urlDestino + "]", pex);
			respError.setCode("-4");
			respError.setMessage("No se pudo contactar al destino: " + pex.getMessage());
			logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
			return Response.status(Response.Status.BAD_GATEWAY).entity(respError).build();
		} catch (Exception ex) {
			logger.error("[" + reqId + "] BROKER - ERROR inesperado llamando al destino", ex);
			respError.setCode("-5");
			respError.setMessage("Error inesperado: " + ex.getMessage());
			logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(respError).build();
		} finally {
			if (respuestaDestino != null) {
				respuestaDestino.close();
			}
		}

		// 3. Passthrough: devolvemos al origen el status y body del destino TAL CUAL.
		String bodyOut = bodyDestino == null ? "" : bodyDestino;
		logger.info("[" + reqId + "] BROKER - RESPONDIENDO al ORIGEN");
		logger.info("[" + reqId + "] Status enviado al origen: " + statusDestino);
		logger.info("[" + reqId + "] Body enviado al origen: " + bodyOut.replaceAll("\\r|\\n", ""));
		logger.info("[" + reqId + "] Duracion total: " + (System.currentTimeMillis() - t0) + "ms");
		logger.info("=====================================================");

		return Response.status(statusDestino)
				.entity(bodyOut)
				.type(MediaType.APPLICATION_JSON)
				.build();
	}

}
