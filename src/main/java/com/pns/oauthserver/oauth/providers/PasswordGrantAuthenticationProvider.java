package com.pns.oauthserver.oauth.providers;

import com.pns.oauthserver.oauth.CustomOauth2Util;
import com.pns.oauthserver.oauth.token.PasswordGrantAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;

import java.security.Principal;
import java.util.*;

public class PasswordGrantAuthenticationProvider implements AuthenticationProvider {
    private static final Logger log = LoggerFactory.getLogger(PasswordGrantAuthenticationProvider.class);
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationService authorizationService;

    public PasswordGrantAuthenticationProvider(OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator, RegisteredClientRepository registeredClientRepository, OAuth2AuthorizationService authorizationService) {
        this.tokenGenerator = tokenGenerator;
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {


        log.trace("Authentication started from PasswordGrantAuthenticationToken: {}", authentication);

        PasswordGrantAuthenticationToken passwordGrantAuthenticationToken =
                (PasswordGrantAuthenticationToken) authentication;

        log.trace("PasswordToken conversion successful from PasswordGrantAuthenticationToken, entering client authentication");

        OAuth2ClientAuthenticationToken clientPrincipal =
                getAuthenticatedClientElseThrowInvalidClient(passwordGrantAuthenticationToken);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        log.trace("Client has been authenticated : {}", clientPrincipal);

        Assert.notNull(registeredClient, "Registered client must not be null");

        // Ensure the client is configured to use this authorization grant type
        if (!registeredClient.getAuthorizationGrantTypes().contains(passwordGrantAuthenticationToken.getGrantType())) {
            log.trace("Client is not configured to support grant type : {}", passwordGrantAuthenticationToken.getGrantType());
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        log.trace("Client is configured to support grant type : {}", passwordGrantAuthenticationToken.getGrantType());

        Map<String, Object> additionalParams = passwordGrantAuthenticationToken.getAdditionalParameters();
        if (additionalParams == null) {
            log.trace("Additional parameters is null");
            OAuth2Error error = new OAuth2Error(
                    OAuth2ErrorCodes.SERVER_ERROR,
                    "Server error: try again later",
                    null
            );
            throw new OAuth2AuthenticationException(error);
        }

        log.trace("Additional parameters is not null, scopes validations started: {}", additionalParams);

        Set<String> requestedScopes = passwordGrantAuthenticationToken.getScopes();
        if (!registeredClient.getScopes().containsAll(requestedScopes)) {
            log.trace("The client has requested for more scopes those are not assigned to it");
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE,
                    "Invalid scopes",
                    null
            );
            throw new OAuth2AuthenticationException(error);
        }

        log.trace("Client has requested for scopes : {}", requestedScopes);

        Authentication principal = (Authentication) passwordGrantAuthenticationToken.getPrincipal();

        log.trace("User credentials are validated moving forward to token generation");
        //GENERATE TOKEN_CONTEXT_BUILDER
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizationGrantType(passwordGrantAuthenticationToken.getGrantType())
                .authorizationGrant(passwordGrantAuthenticationToken)
                .authorizedScopes(requestedScopes);
        // Generate the access token
        OAuth2TokenContext accessTokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();

        OAuth2Token generatedAccessToken = this.tokenGenerator.generate(accessTokenContext);
        if (generatedAccessToken == null) {
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                    "The token generator failed to generate the access token.", null);
            throw new OAuth2AuthenticationException(error);

        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(), requestedScopes);


        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .authorizedScopes(passwordGrantAuthenticationToken.getScopes())
                .principalName(principal.getName())
                .attribute(Principal.class.getName(), principal)
                .authorizationGrantType(passwordGrantAuthenticationToken.getGrantType());


        OAuth2TokenFormat accessTokenFormat = accessTokenContext.getRegisteredClient().getTokenSettings().getAccessTokenFormat();
        authorizationBuilder.token(accessToken, metadata -> {
            if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims());
            }

            metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, false);
            metadata.put(OAuth2TokenFormat.class.getName(), accessTokenFormat.getValue());
        });

        log.trace("Access token generated ");

        /***
         *  Checking refresh token request
         *  */
        log.trace("Checking refresh token request");

        OAuth2RefreshToken refreshToken = null;

        if (requestedScopes.contains(CustomOauth2Util.OAUTH_SCOPE_OFFLINE_ACCESS)) {

            OAuth2TokenContext refreshTokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(refreshTokenContext);
            Assert.notNull(generatedRefreshToken, "Internal error: the generated refresh token is null");
            refreshToken = new OAuth2RefreshToken(generatedRefreshToken.getTokenValue()
                    , generatedRefreshToken.getIssuedAt(), generatedRefreshToken.getExpiresAt());

            authorizationBuilder.refreshToken(refreshToken);

        }
        OidcIdToken idToken;
        if (requestedScopes.contains(OidcScopes.OPENID)) {
            OAuth2TokenContext idTokenContext = tokenContextBuilder.tokenType(CustomOauth2Util.OAUTH2_TOKEN_TYPE_ID_TOKEN).build();
            OAuth2Token generatedIdToken = this.tokenGenerator.generate(idTokenContext);
            if (!(generatedIdToken instanceof Jwt)) {
                OAuth2Error error = new OAuth2Error("server_error", "The token generator failed to generate the ID token.", "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2");
                throw new OAuth2AuthenticationException(error);
            }

            log.trace("Generated id token");

            idToken = new OidcIdToken(generatedIdToken.getTokenValue(), generatedIdToken.getIssuedAt(), generatedIdToken.getExpiresAt(), ((Jwt) generatedIdToken).getClaims());
            authorizationBuilder.token(idToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, idToken.getClaims()));
        } else {
            idToken = null;
        }

        authorizationBuilder.authorizedScopes(requestedScopes);
        OAuth2Authorization authorization = authorizationBuilder.build();

        log.trace("Saving authorization to the cache");
        this.authorizationService.save(authorization);

        /***
         * @implNote Use this map to set any custom response to the token endpoint
         */
        Map<String, Object> additionalParameterResponse = new HashMap<>();

        if (idToken != null) {
            additionalParameterResponse.put("id_token", idToken.getTokenValue());
        }
        log.trace("Added additional parameters to the response");

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken, refreshToken, additionalParameterResponse);
   }

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(PasswordGrantAuthenticationToken authentication) {
        OAuth2ClientAuthenticationToken clientPrincipal = null;

        log.debug("ClientAuthentication requested for client: {}", authentication.getClientId());

        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(authentication.getClientId());

        if(registeredClient == null) {
            throwError(OAuth2ErrorCodes.INVALID_CLIENT,"client_credentials");
        }

        clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient,CustomOauth2Util.OAUTH_PASSWORD_GRANT_CLIENT_VALIDATION_METHOD,authentication.getClientSecret());

        log.trace("Authenticating client: {}", clientPrincipal);

        if (clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }

        log.trace("Unauthenticated client: {}", clientPrincipal);

        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_CLIENT,
                "Password Grant client authentication failed: client_id",
                null
        );

        throw new OAuth2AuthenticationException(error);
    }

    private static void throwError(String errorCode, String parameterName) {
        throwError(errorCode, parameterName, "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1");
    }

    private static void throwError(String errorCode, String parameterName, String errorUri) {
        OAuth2Error error = new OAuth2Error(errorCode, "OAuth 2.0 Parameter: " + parameterName, errorUri);
        throw new OAuth2AuthenticationException(error);
    }
}