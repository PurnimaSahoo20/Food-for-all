//package com.pns.oauthserver.config;
//
//import jakarta.servlet.http.HttpServletRequest;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.lang.Nullable;
//import org.springframework.security.authentication.AuthenticationProvider;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.AuthenticationException;
//import org.springframework.security.core.Transient;
//import org.springframework.security.oauth2.core.*;
//import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
//import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
//import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
//import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
//import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
//import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.AuthenticationConverter;
//import org.springframework.security.web.util.matcher.RequestMatcher;
//import org.springframework.util.Assert;
//import org.springframework.util.StringUtils;
//
//@EnableWebSecurity
//@Configuration(proxyBeanMethods = false)
//public class AuthorizationServerConfigurationWithPublicClientAuthentication extends OAuth2AuthorizationServerConfiguration {
//    // @formatter:off
//    @Bean
//    SecurityFilterChain authorizationServerSecurityFilterChain(
//            HttpSecurity http, RegisteredClientRepository registeredClientRepository) throws Exception {
//        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
//                new OAuth2AuthorizationServerConfigurer();
//        authorizationServerConfigurer
//                .clientAuthentication(clientAuthentication ->
//                        clientAuthentication
//                                .authenticationConverter(
//                                        new PublicClientRefreshTokenAuthenticationConverter())
//                                .authenticationProvider(
//                                        new PublicClientRefreshTokenAuthenticationProvider(registeredClientRepository))
//                );
//        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
//        http
//                .securityMatcher(endpointsMatcher)
//                .authorizeHttpRequests(authorize ->
//                        authorize.anyRequest().authenticated()
//                )
//                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
//                .apply(authorizationServerConfigurer);
//        return http.build();
//    }
//    // @formatter:on
//}
//@Transient
//final class PublicClientRefreshTokenAuthenticationToken extends OAuth2ClientAuthenticationToken {
//    PublicClientRefreshTokenAuthenticationToken(String clientId) {
//        super(clientId, ClientAuthenticationMethod.NONE, null, null);
//    }
//    private PublicClientRefreshTokenAuthenticationToken(RegisteredClient registeredClient) {
//        super(registeredClient, ClientAuthenticationMethod.NONE, null);
//    }
//}
//final class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {
//    @Nullable
//    @Override
//    public Authentication convert(HttpServletRequest request) {
//        // grant_type (REQUIRED)
//        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
//        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
//            return null;
//        }
//        // client_id (REQUIRED)
//        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
//        if (!StringUtils.hasText(clientId)) {
//            return null;
//        }
//        return new PublicClientRefreshTokenAuthenticationToken(clientId);
//    }
//}
//final class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider {
//    private final RegisteredClientRepository registeredClientRepository;
//    PublicClientRefreshTokenAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
//        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
//        this.registeredClientRepository = registeredClientRepository;
//    }
//    @Override
//    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
//        PublicClientRefreshTokenAuthenticationToken publicClientAuthentication =
//                (PublicClientRefreshTokenAuthenticationToken) authentication;
//        if (!ClientAuthenticationMethod.NONE.equals(publicClientAuthentication.getClientAuthenticationMethod())) {
//            return null;
//        }
//        String clientId = publicClientAuthentication.getPrincipal().toString();
//        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
//        if (registeredClient == null) {
//            throwInvalidClient(OAuth2ParameterNames.CLIENT_ID);
//        }
//        if (!registeredClient.getClientAuthenticationMethods().contains(
//                publicClientAuthentication.getClientAuthenticationMethod())) {
//            throwInvalidClient("authentication_method");
//        }
//        return new PublicClientRefreshTokenAuthenticationToken(registeredClient.getClientId());
//    }
//    @Override
//    public boolean supports(Class<?> authentication) {
//        return PublicClientRefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
//    }
//    private static void throwInvalidClient(String parameterName) {
//        OAuth2Error error = new OAuth2Error(
//                OAuth2ErrorCodes.INVALID_CLIENT,
//                "Public client authentication failed: " + parameterName,
//                null
//        );
//        throw new OAuth2AuthenticationException(error);
//    }
//}