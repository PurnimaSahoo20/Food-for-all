package com.pns.oauthserver.oauth.providers;

import com.pns.oauthserver.oauth.token.PublicClientRefreshTokenAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;

public final class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider {
    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientRefreshTokenAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PublicClientRefreshTokenAuthenticationToken publicClientAuthentication =
                (PublicClientRefreshTokenAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(publicClientAuthentication.getClientAuthenticationMethod())) {
            return null;
        }
        String clientId = publicClientAuthentication.getPrincipal().toString();
        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            throwInvalidClient(OAuth2ParameterNames.CLIENT_ID);
        }
        assert registeredClient != null;
        if (!registeredClient.getClientAuthenticationMethods().contains(
                publicClientAuthentication.getClientAuthenticationMethod())) {
            throwInvalidClient("authentication_method");
        }
        return new PublicClientRefreshTokenAuthenticationToken(registeredClient);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PublicClientRefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static void throwInvalidClient(String parameterName) {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_CLIENT,
                "Public client authentication failed: " + parameterName,
                null
        );
        throw new OAuth2AuthenticationException(error);
    }
}