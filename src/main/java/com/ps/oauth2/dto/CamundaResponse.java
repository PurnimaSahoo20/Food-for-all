package com.ps.oauth2.dto;



import lombok.Data;

@Data
public class CamundaResponse {
    private String status;
    private String message;
    private Object items;
    private Object page;
    
    public CamundaResponse() {
        this.status = "";
        this.message = "";
        this.items = null;
    }
    
    public CamundaResponse(CamundaResponseCode status, String message, Object items) {
        this.status = status.toString();
        this.message = message;
        this.items = items;
    }
}
