package com.pns.oauthserver.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.pns.oauthserver.oauth.CustomOauth2Util;
import com.pns.oauthserver.oauth.converter.PasswordGrantTypeConverter;
import com.pns.oauthserver.oauth.converter.PublicClientRefreshTokenAuthenticationConverter;
import com.pns.oauthserver.oauth.providers.PasswordGrantAuthenticationProvider;
import com.pns.oauthserver.oauth.providers.PublicClientRefreshTokenAuthenticationProvider;
import com.pns.oauthserver.repo.UserRepository;
import com.pns.oauthserver.service.UserCRUDService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.FormPostRedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Configuration
public class OAuth2Configuration {

    final UserCRUDService userCRUDService;

    final UserDetailsService userDetailsService;

    final UserRepository userRepository;

    final Environment env;

    private final AuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();

    public OAuth2Configuration(UserCRUDService userCRUDService, UserDetailsService userDetailsService, UserRepository userRepository, Environment env) {
        this.userCRUDService = userCRUDService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.env = env;
    }

    @Bean
    SecurityFilterChain oauthAuthorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer configurer = new OAuth2AuthorizationServerConfigurer();
        configurer
                .clientAuthentication(clientAuthentication ->
                        clientAuthentication
                                .authenticationConverter(
                                        new PublicClientRefreshTokenAuthenticationConverter())
                                .authenticationProvider(
                                        new PublicClientRefreshTokenAuthenticationProvider(registeredClientRepository()))
                );
        RequestMatcher endpointsMatcher = configurer.getEndpointsMatcher();
        http
                .with(configurer, authorizationServer -> authorizationServer
                        .authorizationService(authorizationService())
                        .tokenEndpoint(tokenEndpointConfigurer->
                                tokenEndpointConfigurer.accessTokenRequestConverter(new PasswordGrantTypeConverter(userDetailsService,passwordEncoder())).authenticationProvider(new PasswordGrantAuthenticationProvider(tokenGenerator(),
                                        registeredClientRepository(), authorizationService())
                        ))
                )
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers( "/uploads/**","HbtChk").permitAll()
                                .requestMatchers("/webjars/**", "/images/**", "/css/**", "/assets/**", "/favicon.ico").permitAll()
                                .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2->oauth2.jwt(Customizer.withDefaults()))
//                .formLogin(Customizer.withDefaults())
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .formLogin(formLogin -> formLogin.loginPage("/login").permitAll())
                .oauth2Login(oauth2Login-> oauth2Login
                        .loginPage("/login").permitAll()
                        .successHandler((request, response, authentication) -> {
                    if(authentication.getPrincipal() instanceof OidcUser socialUser && !userCRUDService.createUserIfNotExist(socialUser)){
                            throw new OAuth2AuthenticationException(new OAuth2Error("SERVER ERROR","SOMETHING WENT WRONG",""));
                        }
                    this.delegate.onAuthenticationSuccess(request, response, authentication);
                }))

                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler())

                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .permitAll()
                )
                .apply(configurer)
                .oidc(Customizer.withDefaults())
                .tokenGenerator(tokenGenerator());
        return http.build();
    }
    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository());


        oidcLogoutSuccessHandler.setRedirectStrategy(new FormPostRedirectStrategy());

        return oidcLogoutSuccessHandler;
    }

    @Bean
    RegisteredClientRepository registeredClientRepository() {
        RegisteredClient angularClient = RegisteredClient.withId("react-client")
                .clientId("react-id")
                .clientSecret(passwordEncoder().encode("react-secret"))
                .clientSettings(ClientSettings.builder().requireProofKey(false).requireAuthorizationConsent(false).build())
                .clientName("React Client")
                .redirectUri("http://localhost:5173/authentication/callback")
                .redirectUri("https://oauth.pstmn.io/v1/callback")
                .postLogoutRedirectUri("http://localhost:5173/")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope(CustomOauth2Util.OAUTH_SCOPE_OFFLINE_ACCESS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .authorizationCodeTimeToLive(Duration.ofMinutes(35))
                        .reuseRefreshTokens(false)
                        .refreshTokenTimeToLive(Duration.ofMinutes(7))
                        .build()
                )
                .build();
        RegisteredClient androidClient = RegisteredClient.withId("android-client")
                .clientId("android")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .clientName("Android Client")
                .redirectUri("com.pns.oauth:/oauth2redirect")
                .postLogoutRedirectUri("com.pns.oauth:/oauth2redirect")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope(CustomOauth2Util.OAUTH_SCOPE_OFFLINE_ACCESS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .authorizationCodeTimeToLive(Duration.ofMinutes(35))
                        .reuseRefreshTokens(false)
                        .refreshTokenTimeToLive(Duration.ofMinutes(7))
                        .build()
                )
                .build();
        RegisteredClient springClient = RegisteredClient.withId("spring-client")
                .clientId("spring")
                .clientSecret(passwordEncoder().encode("secret"))
                .clientSettings(ClientSettings.builder().requireProofKey(false).requireAuthorizationConsent(false).build())
                .clientName("Spring Client")
                .redirectUri("http://localhost:8085/login/oauth2/code/custom")
                .postLogoutRedirectUri("http://localhost:8085/login")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientAuthenticationMethod(CustomOauth2Util.OAUTH_PASSWORD_GRANT_CLIENT_VALIDATION_METHOD)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope(CustomOauth2Util.OAUTH_SCOPE_OFFLINE_ACCESS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .authorizationCodeTimeToLive(Duration.ofMinutes(1))
                        .reuseRefreshTokens(false)
                        .refreshTokenTimeToLive(Duration.ofMinutes(35))
                        .build()
                )
                .build();
        return new InMemoryRegisteredClientRepository(angularClient, springClient, androidClient);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator() {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource()));
        jwtGenerator.setJwtCustomizer(jwtCustomizer());
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2TokenGenerator<OAuth2RefreshToken> refreshTokenGenerator = new CustomRefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType()) &&
                    OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().claims(claims -> {
                    String email = context.getPrincipal().getName();
                    userRepository.findByEmail(email).ifPresent(user -> {
                        claims.put("name", user.getName());
                        claims.put("email", user.getEmail());
                        claims.put("role", List.of(user.getRole(),user.getRegion()));
                        claims.put("picture", env.getProperty("spring.application.base-url") +user.getProfileImageUrl());
                        claims.put("initial", user.getRegion()==null||user.getRole()==null);
                        claims.put("region", user.getRegion());
                    });
                });
            }
        };
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        // Generate RSA key pair
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // Build RSA key
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();

        // Create JWK set with the RSA key
        JWKSet jwkSet = new JWKSet(rsaKey);

        // Return a JWKSource that serves the JWK set
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048); // Use 2048 bits for good security
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Error generating RSA key pair", ex);
        }
    }


    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings
                .builder()
                .build();
    }


    //Social Sign In
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration googleClient = ClientRegistration
                .withRegistrationId("google")
                .clientId("560660532789-2750m14i1cihuls196ot8vbr6nadan6h.apps.googleusercontent.com")
                .clientSecret("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")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .scope(Set.of(OidcScopes.OPENID,OidcScopes.EMAIL,OidcScopes.PROFILE))
                .userNameAttributeName("email")
                .redirectUri("http://localhost:9999/login/oauth2/code/google")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        ClientRegistration githubClient = ClientRegistration
                .withRegistrationId("github")
                .clientId("Ov23liVRck9mhNqDoOfo")
                .clientSecret("1bbbc73df0d5a05b41d6bc95ee3becb0aae04ffd")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .scope(Set.of(OidcScopes.OPENID,OidcScopes.EMAIL,OidcScopes.PROFILE))
                .userNameAttributeName("email")
                .redirectUri("http://localhost:9999/login/oauth2/code/github")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
        return new InMemoryClientRegistrationRepository(googleClient,githubClient);
    }



}
