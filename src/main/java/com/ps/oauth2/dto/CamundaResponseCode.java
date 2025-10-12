package com.ps.oauth2.dto;

public enum CamundaResponseCode {
	
	EXISTING_NEW_TASK("01"),
	SUCCESS("01"),
	NO_FURTHER_TASK("02"),
	INCIDENT("03"),
	EXCEPTION("04"),
	PROCESS_COMPLETED("05"),
	FOUND_PROCESS_INSTANCE("01"),
	NO_PROCESS_INSTANCE("02");

	private final String value;
	
	CamundaResponseCode(String value) {
		this.value = value;
		
	}
	 
	public String toString() {
		return this.value;
	}	

}
