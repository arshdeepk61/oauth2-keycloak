package org.example.resourceserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The heart of the Resource Server.
 *
 * Every HTTP request passes through Spring Security's filter chain. Here we say:
 *   1. Some endpoints are public, the rest require a valid JWT.
 *   2. Validate the JWT as a "Bearer token" (the OAuth2 Resource Server support).
 *   3. Be STATELESS - no HTTP session, no cookie. The token IS the identity,
 *      and it is re-validated on every single request.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtConverter) throws Exception {
        http
            // Define authorization rules per URL pattern. Rules are evaluated top-down.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public").permitAll()            // open to everyone
                .requestMatchers("/api/admin").hasRole("ADMIN")        // needs ROLE_ADMIN
                .requestMatchers("/api/user").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()                          // everything else: just be logged in
            )
            // Turn on Resource Server mode with JWT validation. Because we set
            // issuer-uri in application.yml, Spring auto-downloads Keycloak's
            // public keys (JWKS) and validates signature + issuer + expiry for us.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
            )
            // An API has no login form and no server-side session. Each request
            // must carry its own token. This also disables JSESSIONID cookies.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CSRF protection guards cookie/session-based browser apps. A stateless
            // token API is not vulnerable to CSRF, so we disable it.
            .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * By default Spring maps the JWT's "scope"/"scp" claim to authorities.
     * Keycloak instead puts roles under realm_access.roles, so we plug in a
     * custom converter (see KeycloakRoleConverter) to translate those into
     * Spring's ROLE_* authorities used by hasRole(...) above.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }
}
