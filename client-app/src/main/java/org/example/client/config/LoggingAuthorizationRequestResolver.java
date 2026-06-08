package org.example.client.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * A learning aid. Wraps the real authorization-request resolver and LOGS the
 * OAuth2AuthorizationRequest that Spring builds when you click "Login".
 *
 * The point: that object is saved in your HTTP session. The URL only gets the
 * PUBLIC parts (state, nonce, code_challenge); the SECRET part (code_verifier)
 * is kept server-side in `attributes` and never leaves the server. This log
 * lets you see both side by side.
 */
public class LoggingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthorizationRequestResolver.class);

    private final OAuth2AuthorizationRequestResolver delegate;

    public LoggingAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return logIt(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return logIt(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest logIt(OAuth2AuthorizationRequest req) {
        // delegate.resolve(...) returns null for any path that isn't the login
        // entry point, so we only log when an actual login is being started.
        if (req == null) {
            return null;
        }
        log.info("================ AUTHORIZATION REQUEST GENERATED & SAVED IN SESSION ================");
        log.info("  state (anti-CSRF)              = {}", req.getState());
        log.info("  redirectUri                    = {}", req.getRedirectUri());
        log.info("  scopes                         = {}", req.getScopes());
        log.info("  --> PUT IN THE BROWSER URL     = {}", req.getAdditionalParameters()); // nonce, code_challenge, S256
        log.info("  --> KEPT SECRET (server-side)  = {}", req.getAttributes());            // nonce, code_VERIFIER
        log.info("  full authorize URL             = {}", req.getAuthorizationRequestUri());
        log.info("===================================================================================");
        return req;
    }
}
