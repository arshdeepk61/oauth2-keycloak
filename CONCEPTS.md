# Interview Guide: Spring Security + Keycloak + OAuth2 + OIDC + JWT

A from-zero walkthrough of every concept in this project, ordered so each builds
on the last. Read top-to-bottom once; then use the **Interview Q&A** at the end to
self-test. Code references point at the files in this repo.

---

## 1. The mental model: separate *authentication* from *authorization*

These two words sound alike and are constantly confused — nailing the distinction
is the single most important thing in this interview.

- **Authentication (AuthN) = "Who are you?"** Proving identity (login).
- **Authorization (AuthZ) = "What are you allowed to do?"** Granting access.

| | Standard | Question it answers | Token |
|---|---|---|---|
| **OAuth2** | RFC 6749 | Authorization — *what can the bearer access?* | **access token** |
| **OIDC** | built on top of OAuth2 | Authentication — *who is the user?* | **ID token** |

> One-liner for the interview: **"OAuth2 is an authorization framework; OpenID
> Connect is a thin authentication layer on top of it. JWT is just a token
> *format* both can use. Keycloak is the server that implements all of this."**

---

## 2. The four OAuth2 roles (actors)

Map these onto this project — interviewers love this.

1. **Resource Owner** — the human/user who owns the data. → *alice*.
2. **Client** — the app that wants to act on the user's behalf. → **client-app** (port 8090).
3. **Authorization Server** — authenticates the user and issues tokens. → **Keycloak** (8081).
4. **Resource Server** — hosts the protected API, accepts tokens. → **resource-server** (8082).

(In OIDC vocabulary the Client is also called the **Relying Party (RP)**, and the
Authorization Server is the **OpenID Provider (OP)**.)

---

## 3. Why OAuth2 exists (the problem it solves)

Before OAuth2, to let App A use your data in App B you'd give App A your B
password. Terrible: A can do *anything*, forever, and you can't revoke it without
changing your password. OAuth2 fixes this with **delegated authorization**: the
user authenticates *only* at the Authorization Server, which hands the client a
**scoped, expiring token** instead of the password. The client never sees the
credentials.

---

## 4. Grant types (flows) — and which to use

A "grant type" is the recipe by which a client gets a token.

- **Authorization Code** ← *this project uses it.* The browser is redirected to
  Keycloak, the user logs in, Keycloak redirects back with a short-lived
  **authorization code**, and the client exchanges that code for tokens over a
  **back channel** (server-to-server). The token never travels through the browser
  URL. This is the **recommended flow for server-side web apps**.
- **Authorization Code + PKCE** ← *also enabled here.* PKCE (Proof Key for Code
  Exchange) adds a one-time secret (`code_verifier`/`code_challenge`) so a stolen
  code can't be redeemed by an attacker. **Mandatory for public clients** (SPAs,
  mobile) and now recommended for *all* clients.
- **Client Credentials** — no user at all; one service calls another using its own
  client id/secret. (Machine-to-machine.)
- **Refresh Token** — not a login; exchange a refresh token for a fresh access
  token when the old one expires.
- **Implicit** and **Resource Owner Password Credentials (ROPC)** — **legacy /
  discouraged.** Implicit put tokens in the URL; ROPC needs the user's raw
  password. We enable ROPC here *only* so you can grab a token with `curl` for
  learning (see README). Don't use it in production.

> Interview trap: *"Why Authorization Code over Implicit?"* → Implicit returned the
> token directly in the redirect URL fragment (exposed in browser history, logs,
> referrer headers) and had no client authentication. Code flow keeps the token on
> the back channel and adds PKCE.

---

## 5. The three tokens

| Token | Format | Audience | Purpose | Lifetime |
|-------|--------|----------|---------|----------|
| **Access token** | JWT (in Keycloak) | the Resource Server | call APIs — "authorization" | short (mins) |
| **ID token** | always JWT | the Client | tell the client who logged in — "authentication" | short |
| **Refresh token** | opaque (usually) | the Authorization Server | get new access tokens without re-login | long (mins–hours) |

Key points interviewers probe:
- The **access token is for the API; the ID token is for the client.** Never send
  the ID token to a resource server, and a well-behaved resource server should
  reject it (wrong audience).
- Access tokens are **short-lived on purpose** — if leaked, the damage window is
  small. Refresh tokens let you stay logged in without re-prompting.
- In this project, after login the client holds all three (see
  `WebClientConfig` — it auto-refreshes the access token using the refresh token).

---

## 6. JWT anatomy (be able to draw this)

A JWT is three Base64URL parts joined by dots: **`header.payload.signature`**.

```
eyJhbGciOiJSUzI1NiIsImtpZCI6Ii4uLiJ9 . eyJzdWIiOiI...cm9sZXMiOlsiVVNFUiJdfQ . <signature>
        HEADER (alg, kid)                         PAYLOAD (claims)              SIGNATURE
```

- **Header** — `alg` (e.g. `RS256` = RSA + SHA-256) and `kid` (which key signed it).
- **Payload (claims)** — JSON facts. Standard ones:
  - `iss` (issuer — who minted it, e.g. `http://localhost:8081/realms/demo`)
  - `sub` (subject — the user id)
  - `aud` (audience — who it's for)
  - `exp` / `iat` / `nbf` (expiry / issued-at / not-before)
  - `scope`, and Keycloak-specific `realm_access.roles`, `preferred_username`, `email`.
- **Signature** — the issuer signs `header.payload` with its **private key**.
  Anyone can verify with the matching **public key**.

Critical understanding for the interview:
- **A JWT is signed, not encrypted.** Anyone can read the payload (paste it into
  jwt.io). Never put secrets in it. Integrity, not confidentiality.
- **RS256 (asymmetric)** is normal for OAuth2: Keycloak signs with a private key
  the resource server never sees; the resource server verifies with the **public**
  key. Contrast **HS256 (symmetric)**, where signer and verifier share one secret —
  fine for a single app, bad when many services must verify.
- **Stateless validation:** because the resource server can verify the signature
  locally, it does **not** call Keycloak on every request. It only fetches the
  public keys once (and caches them). That's why JWT scales well.

### Where does the public key come from? — JWKS
The resource server discovers everything from the **issuer URL**
(`application.yml` → `issuer-uri`). It calls
`<issuer>/.well-known/openid-configuration` (the **discovery document**), finds the
`jwks_uri`, and downloads the **JWKS** (JSON Web Key Set) — Keycloak's public keys.
The token's `kid` header says which key to use. This is exactly what Spring's
`spring-boot-starter-oauth2-resource-server` automates for you.

---

## 7. What the Resource Server actually validates

On every request carrying `Authorization: Bearer <jwt>`, Spring checks:
1. **Signature** — verified against the JWKS public key named by `kid`.
2. **`exp`** — not expired (and `nbf`/`iat` are sane).
3. **`iss`** — equals the configured issuer.
4. **(should) `aud`** — token was meant for this API. *Note:* by default Spring's
   issuer-uri setup validates signature + expiry + issuer, **not audience**.
   Adding audience validation (a custom `OAuth2TokenValidator`) is a common
   "how would you harden this?" answer.
5. **Authorization** — your rules: `hasRole("ADMIN")` etc. (see `SecurityConfig`).

If 1–4 fail → **401 Unauthorized** (bad/expired/missing token). If 5 fails →
**403 Forbidden** (valid token, insufficient rights). Know that 401-vs-403
distinction cold.

---

## 8. Keycloak concepts

- **Realm** — an isolated tenant: its own users, clients, roles, keys. Ours is
  `demo`. The master realm is only for administering Keycloak itself.
- **Client** — an application registered in the realm (`demo-client`). Two flavors:
  - **Confidential** — can keep a secret (server-side apps like our client-app).
    Authenticates to the token endpoint with `client_secret`.
  - **Public** — can't keep a secret (SPA, mobile). Must use PKCE instead.
- **Roles** — **realm roles** (global, e.g. `USER`, `ADMIN` — what we use) vs
  **client roles** (scoped to one client). Realm roles land in the token under
  `realm_access.roles`; client roles under `resource_access.<client>.roles`.
- **Scopes / client scopes** — bundles of claims/permissions the client requests
  (`openid`, `profile`, `email`). Requesting `openid` is what makes it OIDC and
  triggers an ID token.
- **Protocol mappers** — rules that decide which user attributes/roles get copied
  into the token. (e.g. the built-in mapper that puts realm roles into
  `realm_access.roles`.)
- **Endpoints** (all under `/realms/demo/protocol/openid-connect/`): `auth`
  (authorize), `token`, `userinfo`, `logout`, `certs` (JWKS).

---

## 9. How Spring Security implements all this

### The filter chain
Spring Security is a chain of servlet **filters** in front of your controllers.
Each request runs the chain; a `SecurityFilterChain` bean (we define one per app)
configures it. Authorization rules (`authorizeHttpRequests`) and the auth mechanism
(`oauth2Login` or `oauth2ResourceServer`) are declared there.

### Two different starters for two different jobs
- `spring-boot-starter-oauth2-**client**` → makes client-app a login client
  (drives the Authorization Code flow, holds tokens). Config under
  `spring.security.oauth2.**client**` with **provider** (where the IdP is) +
  **registration** (who we are).
- `spring-boot-starter-oauth2-**resource-server**` → makes resource-server validate
  bearer JWTs. Config is just `spring.security.oauth2.**resourceserver**.jwt.issuer-uri`.

### Roles → authorities
Spring's `hasRole("ADMIN")` checks for an authority literally named `ROLE_ADMIN`.
Keycloak emits roles as plain strings under `realm_access.roles`. The bridge is
`KeycloakRoleConverter` (this repo): it reads those strings and prefixes `ROLE_`.
A *classic* interview "why doesn't my role work?" bug is forgetting this mapping.

### Stateless vs session
- **Resource server is STATELESS** (`SessionCreationPolicy.STATELESS`): no session,
  no cookie; the token is the whole identity and is re-checked every request. CSRF
  is disabled because there's no session cookie to abuse.
- **Client app is session-based**: after login the user gets a session cookie; the
  tokens are stored server-side against that session. CSRF protection stays **on**.

---

## 10. Security hardening concepts (the "how would you make this production-ready?")

- **PKCE** — already enabled (`SecurityConfig` in client-app). Stops auth-code
  interception.
- **`state` parameter** — random value the client sends and checks on callback to
  prevent **CSRF on the login flow**. Spring handles it automatically.
- **`nonce`** — random value bound into the **ID token** to prevent replay; OIDC
  feature, Spring handles it.
- **HTTPS everywhere** — tokens are bearer credentials; anyone holding one can use
  it. TLS is non-negotiable in production (we use `sslRequired: none` only for local dev).
- **Short access-token lifetime + refresh tokens** — limit leak damage.
- **Validate `aud`** on the resource server (see §7).
- **Token storage** — for SPAs, prefer the **Backend-for-Frontend (BFF)** pattern
  (tokens stay on a server, browser gets only a session cookie) over storing tokens
  in `localStorage` (XSS-stealable). Our client-app *is* effectively a BFF.
- **Logout** — RP-initiated logout (implemented here) ends both the local session
  and Keycloak's SSO session, so the user can't silently re-login.

---

## 11. Glossary (fast recall)

- **JWKS** — JSON Web Key Set: the issuer's published public keys.
- **Bearer token** — "whoever bears it may use it"; sent as `Authorization: Bearer`.
- **Opaque token** — random string with no readable content; must be introspected
  at the auth server (contrast JWT, which is self-contained).
- **Token introspection** — asking the auth server "is this token valid?" (used for
  opaque tokens; RFC 7662).
- **Back channel / front channel** — server-to-server (secure) vs via-the-browser.
- **Discovery document** — `/.well-known/openid-configuration`, lists all endpoints.
- **SSO** — one login at Keycloak works across all its clients.

---

## 12. Interview Q&A (self-test)

**Q: Difference between OAuth2 and OIDC?**
A: OAuth2 is an *authorization* framework — it issues access tokens so a client can
call APIs on a user's behalf. OIDC adds an *authentication* layer on top, returning
an **ID token** (always a JWT) that tells the client who the user is. Request the
`openid` scope to turn an OAuth2 flow into an OIDC one.

**Q: Is a JWT encrypted?**
A: No — it's **signed**, not encrypted (by default). The payload is Base64, readable
by anyone. Signing gives integrity/authenticity, not confidentiality. Never put
secrets in a JWT; rely on HTTPS for transport confidentiality.

**Q: How does the resource server validate a token without calling Keycloak each time?**
A: It fetches Keycloak's **public keys (JWKS)** once via the discovery document,
then verifies each token's **signature** locally, plus checks `exp` and `iss`. This
is stateless and scales. It only re-fetches keys if a new `kid` appears or keys rotate.

**Q: Access token vs ID token vs refresh token?**
A: Access token → sent to APIs (authorization), short-lived. ID token → consumed by
the client to learn the user's identity (authentication). Refresh token → exchanged
at the auth server for new access tokens without re-login, longer-lived, usually opaque.

**Q: Why Authorization Code + PKCE instead of Implicit?**
A: Implicit exposed tokens in the redirect URL (history/logs/referrer) and didn't
authenticate the client. Code flow keeps tokens on the back channel; PKCE binds the
code to the requesting client so an intercepted code is useless.

**Q: 401 vs 403?**
A: 401 = not authenticated (missing/invalid/expired token). 403 = authenticated but
not authorized (valid token, lacks the required role/scope).

**Q: Confidential vs public client?**
A: Confidential clients can safely store a secret (server-side) and authenticate to
the token endpoint with it. Public clients (SPA/mobile) can't keep a secret, so they
must use PKCE and never hold a client secret.

**Q: Where do Keycloak roles show up and how does Spring use them?**
A: Realm roles appear under `realm_access.roles` in the access token. Spring's
`hasRole("X")` looks for authority `ROLE_X`, so you convert each role string by
prefixing `ROLE_` (the `KeycloakRoleConverter` here).

**Q: What is a realm in Keycloak?**
A: An isolated security domain — its own users, clients, roles, and signing keys.
Different realms don't share anything; great for multi-tenant separation.

**Q: How would you harden this for production?**
A: Enforce HTTPS, validate the `aud` claim, keep access tokens short, rotate signing
keys, store client secrets in a vault, use PKCE, prefer BFF over browser token
storage, and configure proper CORS and RP-initiated logout.

**Q: What does `issuer-uri` actually do in Spring?**
A: It triggers OIDC discovery: Spring calls
`<issuer>/.well-known/openid-configuration`, learns the `jwks_uri` and other
endpoints, downloads the public keys, and wires up JWT validation (signature +
issuer + expiry) — all from that one URL.

**Q: What's the difference between a JWT and an opaque token?**
A: A JWT is self-contained — the resource server can validate and read it locally.
An opaque token is a meaningless reference string; the resource server must call the
auth server's **introspection** endpoint to validate it and learn its claims.
```
