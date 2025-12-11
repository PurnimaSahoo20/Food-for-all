package com.pns.oauthserver.model.dto;

import lombok.Data;

@Data
public class UserProfileDTO {
    private String name;
    private String email;
    private String role;
    private String region;
    private String profileImageUrl;
    private boolean initialSetup;
}