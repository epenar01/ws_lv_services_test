package com.wsst.beans;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RespTest {
	
	private String code;
	private String message;
	private String folio_number;
	
	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getFolio_number() {
		return folio_number;
	}

	public void setFolio_number(String folio_number) {
		this.folio_number = folio_number;
	}

	public String toString() {
		
		 StringBuilder strB =  new StringBuilder();
		 
		 strB.append("[")
		 		.append("code:").append(code).append(" ")
		 		.append("message:").append(message).append(" ")
		 		.append("folio_numbre:").append(folio_number).append("]");
		 
	     return strB.toString();
	   }
}
