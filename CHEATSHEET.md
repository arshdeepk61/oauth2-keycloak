# OAuth2 / OIDC / JWT / Keycloak + Spring Security — One-Page Cheat-Sheet

**Core line:** *OAuth2 = authorization framework (access token). OIDC = thin authentication layer on top (ID token). JWT = the token **format**. Keycloak = the server implementing all of it.*

### The 4 roles (map to this project)
**Resource Owner** = user (alice) · **Client** = client-app:8080 (a.k.a. OIDC *Relying Party*) · **Authorization Server** = Keycloak:8081 (OIDC *Provider*) · **Resource Server** = resource-server:8082 (the API).

### The 3 tokens
| Token | For | Answers | Format | Life |
|---|---|---|---|---|
| **Access** | the API | "what may bearer do?" (AuthZ) | JWT | short |
| **ID** | the Client | "who is the user?" (AuthN) | JWT | short |
| **Refresh** | the Auth Server | "give me a new access token" | opaque | long |

### Grant types
**Authorization Code (+PKCE)** = server web apps & SPAs ✅ (what we use). **Client Credentials** = machine-to-machine, no user. **Refresh Token** = renew without re-login. **Implicit / Password (ROPC)** = legacy, avoid.

### JWT anatomy = `header.payload.signature` (Base64URL, dot-separated)
- **header**: `alg` (RS256), `kid` (key id). **payload (claims)**: `iss` issuer, `sub` user id, `aud` audience, `exp/iat/nbf`, `realm_access.roles`, `preferred_username`. **signature**: issuer signs with **private** key; verifier checks with **public** key.
- **Signed, NOT encrypted** — payload is readable (jwt.io). Integrity, not secrecy. Never put secrets in it.
- **RS256 (asymmetric)** = Keycloak signs w/ private key, API verifies w/ public key (normal). **HS256** = shared secret (single app only).

### How the API validates a token (stateless — no call to Keycloak per request)
`issuer-uri` → fetch `/.well-known/openid-configuration` → get `jwks_uri` → download **JWKS** (public keys, cached). Per request check: **signature** (key by `kid`) + **exp** + **iss** (+ should check **aud**). Roles checked via Spring rules.

### Keycloak terms
**Realm** = isolated tenant (own users/clients/roles/keys). **Client**: *confidential* (keeps secret, server apps) vs *public* (no secret → must use PKCE, SPA/mobile). **Realm roles** → `realm_access.roles`; **client roles** → `resource_access.<id>.roles`. **Scopes** (`openid` triggers OIDC + ID token). **Protocol mappers** put attrs/roles into tokens.

### Spring Security wiring
`oauth2-client` starter → login flow + holds tokens (config: provider + registration). `oauth2-resource-server` starter → validates bearer JWT (config: `issuer-uri`). `hasRole("ADMIN")` needs authority `ROLE_ADMIN` → convert `realm_access.roles` with a `Converter` (KeycloakRoleConverter). API is **STATELESS + CSRF off**; web client is **session-based + CSRF on**.

---
## Likely interview Q&A (memorize the bold)

**OAuth2 vs OIDC?** OAuth2 authorizes (access token for APIs); **OIDC authenticates** (ID token = who the user is); request `openid` scope to enable it.

**Is a JWT encrypted?** **No — signed, not encrypted.** Gives integrity/authenticity, not confidentiality. Use HTTPS for transport secrecy; never store secrets in it.

**How does the API validate without calling Keycloak each time?** Downloads Keycloak's **public keys (JWKS) once**, verifies signature locally + checks `exp`/`iss`. Stateless → scales.

**Access vs ID vs refresh token?** Access → APIs (AuthZ). ID → client learns identity (AuthN). Refresh → get new access tokens w/o re-login.

**Auth Code vs Implicit — why Code?** Implicit exposed tokens in the URL & didn't authenticate the client. **Code keeps tokens on the back channel; PKCE binds the code to the client** so a stolen code is useless.

**What is PKCE?** Proof Key for Code Exchange — client sends `code_challenge`, later proves `code_verifier`; stops auth-code interception. **Mandatory for public clients.**

**401 vs 403?** **401 = not authenticated** (missing/invalid/expired token). **403 = authenticated but not authorized** (valid token, lacks role/scope).

**Confidential vs public client?** Confidential can store a secret (server). Public can't → uses PKCE, no secret.

**Where do Keycloak roles appear & how does Spring use them?** Realm roles in `realm_access.roles`; map each to `ROLE_<name>` so `hasRole()` works.

**What is a realm?** Isolated security domain — own users, clients, roles, signing keys (multi-tenant).

**JWT vs opaque token?** JWT is self-contained (validate locally). Opaque = reference string → must call **introspection** endpoint.

**`state` vs `nonce`?** `state` = CSRF protection on the login redirect. `nonce` = bound into ID token to prevent replay.

**What does `issuer-uri` do in Spring?** Triggers OIDC discovery → finds endpoints + `jwks_uri`, downloads keys, wires JWT validation — all from one URL.

**Harden for production?** HTTPS · validate `aud` · short access-token TTL · rotate keys · secrets in a vault · PKCE · prefer **BFF** over browser token storage (XSS) · RP-initiated logout · proper CORS.

**SSO / token storage / logout** — SSO: one Keycloak login works across all its clients. SPA tokens in `localStorage` = XSS-stealable → use **Backend-for-Frontend**. RP-initiated logout ends both local session + Keycloak SSO session.
