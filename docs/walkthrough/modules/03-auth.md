# Module 03: ed-iam-auth

`ed-iam-auth` is the authentication and token issuance engine of `ed-iam`. It bridges incoming credentials (passwords, OIDC tokens, API keys) into authenticated domain identities, calculates user permissions via single-pass resolution, and issues signed JWT tokens.

---

## 1. Architectural Position

`ed-iam-auth` implements the primary `AuthenticateUserUseCase` driving port and orchestrates outbound SPI providers:

```
 HTTP POST /api/v1/auth/login
                 │
                 ▼
 ┌────────────────────────────────────────────────────────┐
 │ AuthController                                         │
 └───────────────────────┬────────────────────────────────┘
                         │ (LoginCommand)
                         ▼
 ┌────────────────────────────────────────────────────────┐
 │ AuthenticationService                                  │
 │   Coordinates verification, access resolution, and     │
 │   token minting                                        │
 └───────────┬────────────────────────────┬───────────────┘
             │ (1. Authenticate)          │ (2. Resolve)
             ▼                            ▼
 ┌───────────────────────────┐  ┌─────────────────────────┐
 │ AuthenticationProvider-   │  │ EffectiveAccessResolver │
 │ Router                    │  │ - Direct Roles          │
 │ ├── LocalPasswordProvider │  │ - Group Inherited Roles │
 │ ├── OidcAuthProvider      │  │ - Permissions           │
 │ └── ApiKeyAuthProvider    │  │ - Scope Boundaries      │
 └───────────────────────────┘  └─────────────┬───────────┘
                                              │ (3. Sign Claims)
                                              ▼
                                ┌─────────────────────────┐
                                │ TokenProviderPort       │
                                │ (Issues TokenResponse)  │
                                └─────────────────────────┘
```

---

## 2. Key Mechanisms

### 2.1 Pluggable Authentication Providers (`AuthenticationProvider`)
The router inspects the credentials payload and delegates to the appropriate provider:
* **Local Password**: Queries `UserRepository` by email, verifies password using `BCryptPasswordEncoderAdapter`.
* **OIDC / Federated Identity**: Validates external token, invokes `FederatedIdentityService` to link or auto-provision local `User` records.
* **API Key (M2M)**: Validates service account API keys via the optional `ApiKeyValidatorPort` SPI bean.

### 2.2 Effective Access Resolution (`EffectiveAccessResolver`)
Rather than repeatedly evaluating database relationships per request, `EffectiveAccessResolver` compiles an immutable snapshot:
1. Fetches direct role assignments (`UserRoleAssignment`).
2. Fetches user group memberships (`UserGroupMembership`).
3. Fetches group-level role assignments (`GroupRoleAssignment`).
4. Merges all permissions and accessible scope IDs.
5. Emits `EffectiveAccess`, which is encoded directly into the JWT token claims.

### 2.3 Post-Login Tenant Context Switching (`/api/v1/auth/switch-tenant`)
Users with access to multiple tenants receive `availableTenantIds` in their profile. Calling `POST /api/v1/auth/switch-tenant`:
1. Validates that the user is authorized in the target tenant.
2. Runs `EffectiveAccessResolver` against the target tenant.
3. Issues a new JWT access token immediately bound to the new `tenantId`.

---

## 3. Key Classes & Responsibilities Reference Table

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `IamAuthAutoConfiguration` | Spring AutoConfiguration | Registers auth beans (`AuthenticationService`, `EffectiveAccessResolver`, `FederatedIdentityService`, `BCryptPasswordEncoderAdapter`, providers, and `AuthController`). |
| `AuthController` | Driving REST Controller | Exposes `/api/v1/auth/login`, `/api/v1/auth/refresh-token`, and `/api/v1/auth/switch-tenant`. |
| `LoginRequest` | REST DTO Record | Inbound JSON body for login credentials (`email`, `password`, optional `tenantId`). |
| `RefreshTokenRequest` | REST DTO Record | Inbound JSON body containing the refresh token. |
| `SwitchTenantRequest` | REST DTO Record | Inbound JSON body containing the target `tenantId` to switch into. |
| `AuthenticationService` | Application Service | Implements `AuthenticateUserUseCase`. Orchestrates authentication providers, tenant resolution, effective access compilation, and token issuance. |
| `EffectiveAccessResolver` | Application Service | Aggregates direct user roles, inherited group roles, combined permissions, and accessible organizational scope boundaries into `EffectiveAccess`. |
| `FederatedIdentityService` | Application Service | Handles federated identity linking and auto-provisioning for external OIDC/OAuth2 users. |
| `BCryptPasswordEncoderAdapter` | Security Adapter | Implements `PasswordEncoderPort` using Spring Security's BCrypt implementation. |
| `LocalPasswordAuthProvider` | Security Provider Adapter | Implements `AuthenticationProvider` for local email/password credentials against the database. |
| `OidcAuthProvider` | Security Provider Adapter | Implements `AuthenticationProvider` for external OpenID Connect tokens. |
| `ApiKeyAuthProvider` | Security Provider Adapter | Implements `AuthenticationProvider` for machine-to-machine API keys via `ApiKeyValidatorPort`. |
