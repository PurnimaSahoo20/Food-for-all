package com.ps.oauth2;

import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class OauthConfiguration {
	
	@Value("${spring.datasource.url}")
	private String jdbcUrl;
	
	@Value("${spring.datasource.password}")
	private String jdbcPassword;
	
	@Value("${spring.datasource.username}")
	private String jdbcUserName;
	
	
	
    @Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http)  throws Exception{
    	OAuth2AuthorizationServerConfigurer oAuth2AuthorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
    	
		 http
		 
		 .cors(cors->cors.disable())
		 .csrf(csrf->csrf.disable())
		 .authorizeHttpRequests(autz->autz.anyRequest().authenticated())
		 .formLogin(Customizer.withDefaults())
		 .apply(oAuth2AuthorizationServerConfigurer)
		 .oidc(Customizer.withDefaults());
		 

		 return http.build();
		
	}
    
	
	@Bean
	UserDetailsService userDetailsService() {
		 return  new JdbcUserDetailsManager(datasource());
	}
	
	
	
	@Bean
	RegisteredClientRepository registeredClientRepository() {
		RegisteredClient client = RegisteredClient.withId("client")
				.clientId("client-id")
				.clientSecret(passwordEncoder().encode("client-secret"))
				.redirectUri("https://oauth.pstmn.io/v1/callback")
				.scope(OidcScopes.EMAIL)
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.scope("offline_access")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.tokenSettings(TokenSettings.builder()
						.authorizationCodeTimeToLive(Duration.ofSeconds(60))
						.accessTokenTimeToLive(Duration.ofMinutes(30))
						.refreshTokenTimeToLive(Duration.ofMinutes(60))
						.reuseRefreshTokens(true)
						.build())
				.build();
		
		
		return  new InMemoryRegisteredClientRepository(client);
	}
	
	DataSource datasource() {
		return new SingleConnectionDataSource(jdbcUrl,jdbcUserName, jdbcPassword, true);
	}
	
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	OAuth2AuthorizationService oAuth2AuthorizationService() {
		return new InMemoryOAuth2AuthorizationService();	}
	
}