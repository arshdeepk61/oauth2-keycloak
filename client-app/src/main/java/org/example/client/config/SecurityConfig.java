package org.example.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for the Client app. This is a classic *session-based* web app:
 * after login, the user gets a session cookie; the tokens live server-side.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ClientRegistrationRepository clients) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error").permitAll()  // landing page is open
                .anyRequest().authenticated()                // everything else triggers login
            )
            // Enable the OAuth2/OIDC login flow (Authorization Code). When an
            // unauthenticated user hits a protected page, Spring redirects them
            // to Keycloak, handles the callback, and exchanges code -> tokens.
            .oauth2Login(login -> login
                .authorizationEndpoint(endpoint -> endpoint
                    // Force PKCE even for this confidential client. PKCE binds the
                    // auth code to this client instance so a stolen code is useless.
                    .authorizationRequestResolver(pkceResolver(clients))
                )
            )
            // RP-initiated logout: clear our session AND tell Keycloak to end its
            // SSO session, then send the user back to our home page.
            .logout(logout -> logout
                .logoutSuccessHandler(oidcLogoutSuccessHandler(clients))
            );

        return http.build();
    }

    /** Wraps the default resolver and switches on PKCE (S256 code challenge). */
    private OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository clients) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clients) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clients);
        handler.setPostLogoutRedirectUri("http://localhost:8080/");
        return handler;
    }
}
