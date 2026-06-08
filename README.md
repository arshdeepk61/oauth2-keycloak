# Spring Security + Keycloak + OAuth2 + OIDC + JWT — Learning Project

A small but **real, runnable** reference that demonstrates the full OAuth2 / OpenID
Connect login flow end-to-end, built specifically as interview-prep study material.

There are two Spring Boot apps and one Keycloak:

| Piece | Role in OAuth2 terms | Port | What it does |
|-------|---------------------|------|--------------|
| **Keycloak** (Docker) | Authorization Server + Identity Provider | 8081 | Logs users in, issues tokens (JWTs) |
| **client-app** | OAuth2 Client / OIDC Relying Party | 8090 | The web app users visit; logs in via Keycloak; calls the API |
| **resource-server** | Resource Server | 8082 | Protected REST API; validates JWTs; enforces roles |

> 📖 **Read [`CONCEPTS.md`](./CONCEPTS.md) for the full interview guide** (every term
> explained, the flow step-by-step, and likely interview questions with answers).

---

## The big picture (Authorization Code flow)

```
                                          ┌─────────────────────┐
                                          │      Keycloak        │
                                          │ (Authorization Server)│
                                          └──────────┬──────────┘
   1. visit protected page    2. redirect to login  │
   ┌──────────┐ ───────────────────────────────────►│
   │ Browser  │ ◄─────────────────────────────────── │  3. user enters
   │  (user)  │    4. redirect back with ?code=...   │     alice/alice
   └────┬─────┘                                       │
        │                                             │
        │  5. browser delivers code to client-app     │
        ▼                                             │
   ┌──────────┐  6. exchange code -> tokens (back ch) │
   │client-app│ ────────────────────────────────────►│
   │ (8090)   │ ◄─────────────────────────────────────  7. id_token + access_token
   └────┬─────┘                                       
        │  8. call API with  Authorization: Bearer <access_token>
        ▼
   ┌──────────────┐  9. validate JWT signature/issuer/expiry using
   │resource-server│     Keycloak's public keys (JWKS), check role
   │   (8082)     │ 10. return data (or 401/403)
   └──────────────┘
```

- **id_token** answers *"who is the user?"* → that's **OIDC (authentication)**.
- **access_token** answers *"what may the bearer do?"* → that's **OAuth2 (authorization)**.
- Both are **JWTs** signed by Keycloak; the resource server trusts them without
  calling Keycloak on every request (it only fetches the public keys once).

---

## Prerequisites

- **JDK 21** — Spring Boot 3.3 does not officially support newer JDKs.
  On this machine JDK 21 is at `C:\Program Files\Java\jdk-21`, but `JAVA_HOME`
  defaults to JDK 26, so **set it per terminal first** (PowerShell):
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
  ```
- **Maven** — not required: use the bundled wrapper `.\mvnw` (it auto-downloads
  Maven 3.9.9 on first run).
- **Docker Desktop** — for Keycloak.

## Run it

Open **three terminals** from the project root (set `JAVA_HOME` to JDK 21 in each
of the two Maven terminals as shown above).

**1) Start Keycloak** (pre-loads the `demo` realm, client, roles, users):

```bash
docker compose up
```
Wait for `Running the server in development mode`. Admin console:
http://localhost:8081 (admin / admin).

**2) Start the Resource Server (the API):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
cd resource-server
..\mvnw.cmd spring-boot:run
```

**3) Start the Client app (the website):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
cd client-app
..\mvnw.cmd spring-boot:run
```

Then open **http://localhost:8090** and click **Login with Keycloak**.

### Test users

| Username | Password | Roles |
|----------|----------|-------|
| `alice`  | `alice`  | USER  |
| `admin`  | `admin`  | USER, ADMIN |

- Log in as **alice** → "Call protected API" → `/api/user` works, `/api/admin` is **403**.
- Log in as **admin** → both endpoints work. (This is role-based authorization live.)
- Click **My Profile** to see the ID-Token claims.
- On the API result page, copy the access token and paste it into https://jwt.io to
  see exactly what's inside a Keycloak JWT.

---

## Try the raw token yourself (great for understanding)

Skip the browser and ask Keycloak for a token directly (Direct Access / password
grant — enabled here for learning; avoid in production):

```bash
curl -s -X POST http://localhost:8081/realms/demo/protocol/openid-connect/token \
  -d "client_id=demo-client" \
  -d "client_secret=demo-client-secret" \
  -d "grant_type=password" \
  -d "username=alice" -d "password=alice" | jq .
```

Copy the `access_token` and call the API directly:

```bash
TOKEN="<paste access_token>"
curl -s http://localhost:8082/api/user  -H "Authorization: Bearer $TOKEN" | jq .
curl -s http://localhost:8082/api/admin -H "Authorization: Bearer $TOKEN"   # 403 for alice
curl -s http://localhost:8082/api/public                                    # works, no token
```

---

## Where to look in the code

| Concept | File |
|---------|------|
| Resource server: validate JWT, role rules, stateless | `resource-server/.../config/SecurityConfig.java` |
| Map Keycloak `realm_access.roles` → Spring `ROLE_*` | `resource-server/.../config/KeycloakRoleConverter.java` |
| Reading claims from a validated token | `resource-server/.../web/ApiController.java` |
| Issuer URL that drives JWKS discovery | `resource-server/.../resources/application.yml` |
| Client: OIDC login + PKCE + RP-initiated logout | `client-app/.../config/SecurityConfig.java` |
| Relaying the access token to the API | `client-app/.../config/WebClientConfig.java` |
| Client registration / provider config | `client-app/.../resources/application.yml` |
| Keycloak realm, client, roles, users | `keycloak/realm-export.json` |
```
