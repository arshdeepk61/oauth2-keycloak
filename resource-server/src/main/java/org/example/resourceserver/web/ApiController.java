package org.example.resourceserver.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The protected API. Authorization for these URLs is configured centrally in
 * SecurityConfig. The methods just return data and demonstrate how to read the
 * validated token (the JWT) directly.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    /** Open endpoint - no token required (see permitAll in SecurityConfig). */
    @GetMapping("/public")
    public Map<String, Object> publicData() {
        return Map.of("message", "This is public. No token needed.");
    }

    /** Requires a valid token whose owner has ROLE_USER or ROLE_ADMIN. */
    @GetMapping("/user")
    public Map<String, Object> userData(@AuthenticationPrincipal Jwt jwt) {
        // @AuthenticationPrincipal Jwt gives you the decoded, validated token.
        // You can read any claim Keycloak put in it.
        return Map.of(
                "message", "Hello from the protected USER endpoint!",
                "subject", jwt.getSubject(),                 // the "sub" claim = user id
                "preferredUsername", jwt.getClaimAsString("preferred_username"),
                "email", jwt.getClaimAsString("email"),
                "issuer", jwt.getIssuer().toString(),        // who minted the token (Keycloak)
                "roles", jwt.getClaims().get("realm_access")
        );
    }

    /** Requires a valid token whose owner has ROLE_ADMIN. */
    @GetMapping("/admin")
    public Map<String, Object> adminData(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "message", "Hello ADMIN! This is sensitive data.",
                "subject", jwt.getSubject()
        );
    }
}
