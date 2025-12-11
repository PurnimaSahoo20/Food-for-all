package com.pns.oauthserver.oauth.converter;

import com.pns.oauthserver.oauth.token.PasswordGrantAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class PasswordGrantTypeConverter implements AuthenticationConverter {

    private static final Logger log = LoggerFactory.getLogger(PasswordGrantTypeConverter.class);

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public PasswordGrantTypeConverter(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);

        if (!AuthorizationGrantType.PASSWORD.getValue().equals(grantType)) {
            return null;
        }

        log.trace("Request entered PasswordGrantTypeConverter");

        MultiValueMap<String, String> parameters = getParameters(request);

        String clientId = parameters.getFirst(OAuth2ParameterNames.CLIENT_ID);
        String clientSecret = parameters.getFirst(OAuth2ParameterNames.CLIENT_SECRET);
        if (clientId == null || clientSecret == null) {
            log.trace("CLIENT AUTHENTICATION METHOD MAY NOT BE CLIENT_SECRET_POST");
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Basic ")) {
                try {
                    String base64Credentials = authorization.substring(6);
                    String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
                    String[] values = credentials.split(":", 2);
                    if (values.length == 2) {
                        clientId = values[0];
                        clientSecret = values[1];
                        parameters.set(OAuth2ParameterNames.CLIENT_ID, clientId);
                        parameters.set(OAuth2ParameterNames.CLIENT_SECRET, clientSecret);
                    }
                } catch (IllegalArgumentException e) {
                    log.trace("Failed to decode Authorization header");
                    throwError(OAuth2ErrorCodes.INVALID_REQUEST, "client_credentials");
                }
            }
        }

        if (!StringUtils.hasText(clientId) || (parameters.get(OAuth2ParameterNames.CLIENT_ID)).size() != 1) {
            log.trace("Invalid parameter: client_id");
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, "client_credentials");
        }

        if (!StringUtils.hasText(clientSecret) || (parameters.get(OAuth2ParameterNames.CLIENT_SECRET)).size() != 1) {
            log.trace("Invalid parameter: client_secret");
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, "client_credentials");
        }

        String username = parameters.getFirst(OAuth2ParameterNames.USERNAME);
        String password = parameters.getFirst(OAuth2ParameterNames.PASSWORD);

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.trace("Invalid parameter: credentials");
            throwError(OAuth2ErrorCodes.INVALID_GRANT, "bad_credentials");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (userDetails == null || !passwordEncoder.matches(password, userDetails.getPassword())) {
            log.trace("Invalid user credentials");
            OAuth2Error error = new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_GRANT,
                    "Invalid username or password",
                    null
            );
            throw new OAuth2AuthenticationException(error);
        }

        Authentication principal = new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(principal);

        Set<String> scopes = null;
        String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
        if (StringUtils.hasText(scope) && (parameters.get(OAuth2ParameterNames.SCOPE)).size() != 1) {
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.SCOPE);
        }

        if (StringUtils.hasText(scope)) {
            scopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
        }

        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, value) -> {
            if (!key.equals(OAuth2ParameterNames.GRANT_TYPE) &&
                    !key.equals(OAuth2ParameterNames.CLIENT_ID) &&
                    !key.equals(OAuth2ParameterNames.CLIENT_SECRET) &&
                    !key.equals(OAuth2ParameterNames.USERNAME) &&
                    !key.equals(OAuth2ParameterNames.PASSWORD) &&
                    !key.equals(OAuth2ParameterNames.SCOPE)) {
                additionalParameters.put(key, value.get(0));
            }
        });

        return new PasswordGrantAuthenticationToken(clientId, clientSecret, principal, scopes, additionalParameters);
    }

    private static MultiValueMap<String, String> getParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>(parameterMap.size());
        parameterMap.forEach((key, values) -> {
            for (String value : values) {
                parameters.add(key, value);
            }
        });
        return parameters;
    }

    private static void throwError(String errorCode, String parameterName) {
        throwError(errorCode, parameterName, "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1");
    }

    private static void throwError(String errorCode, String parameterName, String errorUri) {
        OAuth2Error error = new OAuth2Error(errorCode, "OAuth 2.0 Parameter: " + parameterName, errorUri);
        throw new OAuth2AuthenticationException(error);
    }
}