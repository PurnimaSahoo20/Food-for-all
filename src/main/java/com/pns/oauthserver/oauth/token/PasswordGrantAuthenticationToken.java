package com.pns.oauthserver.oauth.token;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.Transient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.io.Serial;
import java.util.*;

@Transient
public final class PasswordGrantAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = UUID.randomUUID().hashCode();
    private final String clientId;
    private final String clientSecret;
    private final Authentication principal;
    private final Set<String> scopes;
    private final Map<String, Object> additionalParameters;
    private AuthorizationGrantType grantType;

    public PasswordGrantAuthenticationToken(String clientId, String clientSecret, Authentication principal, Set<String> scopes, Map<String, Object> additionalParameters) {
        super(Collections.emptyList());
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.principal = principal;
        this.scopes = scopes;
        this.additionalParameters = additionalParameters;
        this.grantType = AuthorizationGrantType.PASSWORD;
    }

    public Object getPrincipal() {
        return this.principal;
    }

    public Object getCredentials() {
        return this.principal.getCredentials();

    }

    public String getClientId() {
        return clientId;
    }
    public String getClientSecret() {
        return clientSecret;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public Map<String, Object> getAdditionalParameters() {
        return additionalParameters;
    }

    public AuthorizationGrantType getGrantType() {
        return grantType;
    }
}
