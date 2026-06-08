package org.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Client application (a.k.a. the "Relying Party" in OIDC).
 *
 * Unlike the Resource Server, this app DOES log the user in. It redirects the
 * browser to Keycloak, receives an authorization code back, exchanges that code
 * for tokens (id_token + access_token), establishes a session, and then uses
 * the access_token to call the Resource Server on the user's behalf.
 */
@SpringBootApplication
public class ClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }
}
