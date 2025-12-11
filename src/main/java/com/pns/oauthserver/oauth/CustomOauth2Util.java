package com.pns.oauthserver.oauth;

import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

public class CustomOauth2Util {
    /***
     * PARAMETERS
     * */
    public static final String OAUTH_PARAMETER_SCOPE = OAuth2ParameterNames.SCOPE;

    /***
     * GRANT TYPES
     * */
    public static final String OAUTH_GRANT_TYPE_PASSWORD = "password";

    /***
     * SCOPES
     * */
    public static final String OAUTH_SCOPE_OFFLINE_ACCESS = "offline_access";

    public static final OAuth2TokenType OAUTH2_TOKEN_TYPE_ID_TOKEN = new OAuth2TokenType("id_token");

    public static final ClientAuthenticationMethod OAUTH_PASSWORD_GRANT_CLIENT_VALIDATION_METHOD = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
}
