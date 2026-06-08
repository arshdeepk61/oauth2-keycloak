package org.example.client.web;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Web pages for the client app. Demonstrates the three things an OIDC client
 * typically does with its tokens.
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final WebClient webClient;

    @Value("${resource-server.base-url}")
    private String resourceServerBaseUrl;

    public HomeController(WebClient webClient) {
        this.webClient = webClient;
    }

    /** Public landing page. */
    @GetMapping("/")
    public String home() {
        return "home";
    }

    /**
     * Shows the logged-in user's identity, taken from the ID Token.
     * The OidcUser is built by Spring from the id_token Keycloak returned -
     * this is the OIDC (authentication) half of the flow.
     */
    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session, Model model) {
        // session.getId() == the value behind the JSESSIONID cookie in your browser.
        // The tokens are stored server-side AGAINST this session, not in the browser.
        log.info("/profile | sessionId={} | sub={} | user={} | id_token expiresAt={}",
                session.getId(), oidcUser.getSubject(), oidcUser.getPreferredUsername(), oidcUser.getExpiresAt());
        model.addAttribute("name", oidcUser.getPreferredUsername());
        model.addAttribute("email", oidcUser.getEmail());
        model.addAttribute("claims", oidcUser.getClaims());      // everything in the id_token
        return "profile";
    }

    /**
     * Calls the protected Resource Server using the access token.
     * This is the OAuth2 (authorization) half: we present a token to get data.
     */
    @GetMapping("/call-api")
    public String callApi(
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
            HttpSession session,
            Model model) {

        // For learning: show the raw access token so you can paste it into jwt.io.
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // This proves the point: the JWT was minted by Keycloak and stored by Spring
        // against THIS session. We never created it - we just read it back here.
        log.info("/call-api | sessionId={} | principal={} | token expiresAt={} | access_token(head)={}...",
                session.getId(),
                authorizedClient.getPrincipalName(),
                authorizedClient.getAccessToken().getExpiresAt(),
                accessToken.substring(0, Math.min(30, accessToken.length())));

        model.addAttribute("accessToken", accessToken);

        // Call /api/user on the resource server. The WebClient (configured in
        // WebClientConfig) attaches the Bearer token automatically.
        String userResponse = webClient.get()
                .uri(resourceServerBaseUrl + "/api/user")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        model.addAttribute("userResponse", userResponse);

        // Try the admin endpoint too. If the user lacks ROLE_ADMIN this returns
        // 403, which we catch and display - a live demo of authorization.
        String adminResponse;
        try {
            adminResponse = webClient.get()
                    .uri(resourceServerBaseUrl + "/api/admin")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            adminResponse = "Access denied (you are not an ADMIN): " + e.getMessage();
        }
        model.addAttribute("adminResponse", adminResponse);

        return "api-result";
    }
}
