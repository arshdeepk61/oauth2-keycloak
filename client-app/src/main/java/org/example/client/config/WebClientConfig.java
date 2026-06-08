package org.example.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds a WebClient that automatically attaches the logged-in user's access
 * token as "Authorization: Bearer <token>" when calling the Resource Server.
 *
 * This is the bridge between the two apps: the Client obtained the token during
 * login; here we relay it so the Resource Server can authorize the request.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        // Use the currently authenticated user's tokens (and refresh them if expired).
        oauth2Filter.setDefaultOAuth2AuthorizedClient(true);

        return WebClient.builder()
                .apply(oauth2Filter.oauth2Configuration())
                .build();
    }

    /**
     * The manager that knows how to obtain/refresh authorized clients (tokens).
     * Spring Boot does not auto-configure one, so we build it here. Because this
     * is a web app driven by the logged-in user's session, we use the
     * request-scoped DefaultOAuth2AuthorizedClientManager and enable the
     * refresh_token grant so expired access tokens are refreshed automatically.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build();

        DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientRepository);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }
}
