package com.pns.oauthserver.model.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String role;
    private String region;
}