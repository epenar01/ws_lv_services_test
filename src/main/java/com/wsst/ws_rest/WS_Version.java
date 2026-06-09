package com.wsst.ws_rest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.jboss.logging.Logger;

import com.wsst.beans.RespDefault;
import com.wsst.beans.RespTest;
import com.wsst.utils.Utilities;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/test")
public class WS_Version {

	private Logger logger = Logger.getLogger(WS_Version.class);

	/** Formato de la fecha/hora que acompana el health check. */
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String getAirTimeTxt(@Context HttpServletRequest req) {
		logger.info("TEST - GET text/plain desde "
				+ (req != null ? req.getRemoteAddr() + ":" + req.getRemotePort() : "?"));
		return "SERVICE up - " + LocalDateTime.now().format(TS);
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDefaultJson(@Context HttpServletRequest req) {
		logger.info("TEST - GET application/json desde "
				+ (req != null ? req.getRemoteAddr() + ":" + req.getRemotePort() : "?"));

		RespTest wsR = null;
		try {
			wsR = new RespTest();
			wsR.setCode("0");
			wsR.setMessage("SERVICE up - " + LocalDateTime.now().format(TS));
			return Response.ok(wsR, MediaType.APPLICATION_JSON).build();
		} catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
			return Response.status(Response.Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/version")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getVersion(@Context HttpServletRequest req) {
		logger.info("VERSION - GET /version desde "
				+ (req != null ? req.getRemoteAddr() + ":" + req.getRemotePort() : "?"));

		RespDefault wsR = new RespDefault();
		try {
			wsR.setCode("0");
			wsR.setVersion("0.0.2");
			return Response.ok(wsR, MediaType.APPLICATION_JSON).build();
		} catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
			wsR.setCode("-1");
			wsR.setMessage(Utilities.fnStrCleanExcep(ex.getMessage()));
			return Response.status(Response.Status.CONFLICT).entity(wsR).build();
		}
	}
}
