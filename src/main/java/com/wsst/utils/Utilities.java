package com.wsst.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

public class Utilities {

	private static Logger logger = Logger.getLogger(Utilities.class);

	/** Detecta el valor del campo "tarjeta" (PAN) dentro de un JSON. */
	private static final Pattern PAN_EN_JSON = Pattern.compile("\"tarjeta\"\\s*:\\s*\"(\\d+)\"");

	/**
	 * Enmascara un PAN dejando visibles los primeros 6 y los ultimos 4 digitos
	 * (627535******8754), que es el maximo permitido por PCI-DSS. Para valores
	 * cortos oculta todo menos los ultimos 4.
	 */
	public static String fnMaskPan(String pan) {
		if (pan == null) {
			return null;
		}
		String digits = pan.trim();
		int len = digits.length();
		if (len <= 4) {
			return digits;
		}
		StringBuilder sb = new StringBuilder();
		if (len < 10) {
			for (int i = 0; i < len - 4; i++) {
				sb.append('*');
			}
			sb.append(digits.substring(len - 4));
			return sb.toString();
		}
		sb.append(digits, 0, 6);
		for (int i = 0; i < len - 10; i++) {
			sb.append('*');
		}
		sb.append(digits.substring(len - 4));
		return sb.toString();
	}

	/**
	 * Devuelve una copia del JSON con el PAN del campo "tarjeta" enmascarado.
	 * Solo para escribir en logs; nunca altera el body real que se reenvia.
	 */
	public static String fnMaskPanInJson(String json) {
		if (json == null) {
			return null;
		}
		Matcher m = PAN_EN_JSON.matcher(json);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String masked = fnMaskPan(m.group(1));
			m.appendReplacement(sb, Matcher.quoteReplacement("\"tarjeta\":\"" + masked + "\""));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	public static String fnStrCleanExcep(String str) {
		String result = "";

		if (str != null) {
			result = str.replace(",", "");
			result = result.replace("\n", "");
			result = result.replace(":", "");
		}

		return result;
	}

	public static String rellenarConCeros(String value, int size) {

		if (value.length() >= size) {
			return value;
		}

		StringBuffer resultado = new StringBuffer();
		int numZero = 0;

		numZero = size - value.length();

		// System.out.println("numZero:"+numZero);

		for (int i = 0; i < numZero; i++) {
			resultado.append("0");
		}

		resultado.append(value);

		return resultado.toString();
	}
	
	
	public static String rellenarConCeros(long inValue, int size) {
		
		String valueAux =  String.valueOf(inValue);

		if (valueAux.length() >= size) {
			return valueAux;
		}

		StringBuffer resultado = new StringBuffer();
		int numZero = 0;

		numZero = size - valueAux.length();

		// System.out.println("numZero:"+numZero);

		for (int i = 0; i < numZero; i++) {
			resultado.append("0");
		}

		resultado.append(valueAux);

		return resultado.toString();
	}
	
	
	public static String fnQuitarSaltosTab(String inValue) {
		
		if(inValue==null) {
			return inValue;
		}

		if (inValue.contains("\r") || inValue.contains("\n")) {
			inValue = inValue.replaceAll("\\r|\\n", "");
			
		}

		if(inValue.contains("\\\"")) {
			inValue = inValue.replace("\\\"", "\"");
		}
		return inValue;
	}

}