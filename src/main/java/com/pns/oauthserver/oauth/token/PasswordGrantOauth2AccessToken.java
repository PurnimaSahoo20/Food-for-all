package com.pns.oauthserver.oauth.token;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Map;

public class PasswordGrantOauth2AccessToken extends OAuth2AccessTokenAuthenticationToken {

    public PasswordGrantOauth2AccessToken(RegisteredClient registeredClient, Authentication clientPrincipal, OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken, Map<String, Object> additionalParameters) {
        super(registeredClient, clientPrincipal, accessToken, refreshToken, additionalParameters);
    }

    public PasswordGrantOauth2AccessToken(RegisteredClient registeredClient, Authentication clientPrincipal, OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken) {
        super(registeredClient, clientPrincipal, accessToken, refreshToken);
    }

    public PasswordGrantOauth2AccessToken(RegisteredClient registeredClient, Authentication clientPrincipal, OAuth2AccessToken accessToken) {
        super(registeredClient, clientPrincipal, accessToken);
    }
}
