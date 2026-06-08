package org.example.resourceserver.config;

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

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
        if (realmAccess == null || realmAccess.get("roles") == null) {
            return List.of();
        }
        Collection<String> roles = (Collection<String>) realmAccess.get("roles");
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
