package com.pns.oauthserver.oauth.token;

import org.springframework.security.core.Transient;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@Transient
public final class PublicClientRefreshTokenAuthenticationToken extends OAuth2ClientAuthenticationToken {
    public PublicClientRefreshTokenAuthenticationToken(String clientId) {
        super(clientId, ClientAuthenticationMethod.NONE, null, null);
    }

    public PublicClientRefreshTokenAuthenticationToken(RegisteredClient registeredClient) {
        super(registeredClient, ClientAuthenticationMethod.NONE, null);
    }
}