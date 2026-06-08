package org.example.resourceserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Resource Server.
 *
 * A "Resource Server" in OAuth2 terms is the API that owns the protected data.
 * It does NOT log users in. It only checks: "is there a valid access token
 * (a JWT signed by Keycloak) on this request, and does its owner have the
 * right role?" If yes -> serve the data. If no -> 401/403.
 */
@SpringBootApplication
public class ResourceServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResourceServerApplication.class, args);
    }
}
