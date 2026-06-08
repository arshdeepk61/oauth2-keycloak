package org.example.resourceserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates Keycloak's role claims into Spring Security authorities.
 *
 * A Keycloak access token (JWT) carries realm roles like this:
 *   {
 *     "realm_access": { "roles": ["USER", "ADMIN", "default-roles-demo"] },
 *     ...
 *   }
 *
 * Spring's hasRole("ADMIN") actually checks for an authority named "ROLE_ADMIN".
 * So we read realm_access.roles and prefix each with "ROLE_".
 *
 * (If you used CLIENT roles instead, you'd read resource_access.<clientId>.roles.)
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    // NOTE: this converter runs ONLY AFTER the JWT's signature, exp and iss have
    // already been validated. So every log line below is proof the token was valid.
    private static final Logger log = LoggerFactory.getLogger(KeycloakRoleConverter.class);

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
        if (realmAccess == null || realmAccess.get("roles") == null) {
            log.info(">>> JWT VALIDATED (sig/exp/iss OK) but NO realm_access.roles | sub={} iss={}",
                    jwt.getSubject(), jwt.getIssuer());
            return List.of();
        }
        Collection<String> roles = (Collection<String>) realmAccess.get("roles");
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());

        log.info(">>> JWT VALIDATED | sub={} | user={} | iss={} | signed-by kid={} | realm roles={} | mapped authorities={}",
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getIssuer(),
                jwt.getHeaders().get("kid"),   // which JWKS public key verified the signature
                roles,
                authorities);

        return authorities;
    }
}
