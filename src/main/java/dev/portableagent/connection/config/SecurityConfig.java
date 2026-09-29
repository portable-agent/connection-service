package dev.portableagent.connection.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, InternalAuthProperties.class})
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        var decoder =
                NimbusJwtDecoder.withJwkSetUri(properties.jwksUrl().toString()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer().toString()),
                new AudienceValidator(properties.audience()),
                new IdentityValidator()));
        return decoder;
    }

    @Bean
    InternalClientAuthorizationManager internalClientAuthorizationManager(InternalAuthProperties properties) {
        return new InternalClientAuthorizationManager(properties);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, InternalClientAuthorizationManager internalClientAuthorizationManager) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.contentSecurityPolicy(
                        policy -> policy.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/v1/connections/callback")
                        .permitAll()
                        .requestMatchers("/internal/v1/tokens")
                        .access(internalClientAuthorizationManager)
                        .requestMatchers("/api/v1/connections", "/api/v1/connections/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .build();
    }
}
