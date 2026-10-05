# 03. Current Features & Product Roadmap

This document outlines the operational capabilities available in the current release (**v0.4.0**) and defines the forward-looking roadmap for future iterations of `ed-iam`.

---

## 1. Current Capabilities Matrix (v0.4.0)

`ed-iam` delivers multi-tenant identity and access management across a decoupled suite of modules:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Feature Capabilities Matrix                     │
├────────────────────────┬────────────────────────────────┬──────────────┤
│ Capability Area        │ Description                    │ Module       │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Multi-Tenancy          │ RFC 9562 UUIDv7 isolation,     │ ed-iam-core  │
│                        │ auto-resolution, and host      │              │
│                        │ TenantContextBridge hook       │              │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ RBAC & Scope Trees     │ Path-indexed hierarchical      │ ed-iam-core  │
│                        │ organizational trees, group    │              │
│                        │ role inheritance, O(1) subtree │              │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Token & Security       │ JJWT 0.12.x HMAC-SHA256,       │ ed-iam-      │
│ Context                │ Java 25 ScopedValue context,   │ resource-    │
│                        │ @RequirePermission, SpEL @iam  │ server       │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Authentication         │ Local BCrypt password hashing, │ ed-iam-auth  │
│ Providers              │ OIDC federated identities,     │              │
│                        │ M2M API Keys (ApiKeyValidator) │              │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Token & Tenant Switch  │ Single-pass EffectiveAccess,   │ ed-iam-auth  │
│                        │ /api/v1/auth/switch-tenant     │              │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Management REST APIs   │ /api/v1/users (with dynamic    │ ed-iam-      │
│                        │ filtering & pagination),       │ management   │
│                        │ /roles, /groups, /scopes       │              │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Database & Schema      │ Isolated iam_* tables, Spring  │ ed-iam-      │
│                        │ Data JPA, Liquibase migrations │ management   │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Turnkey Starter        │ Spring Boot AutoConfiguration, │ ed-iam-      │
│                        │ zero-config defaults           │ starter      │
├────────────────────────┼────────────────────────────────┼──────────────┤
│ Interactive Reference  │ Clinical personas (Doctor,     │ samples/     │
│ Application            │ Nurse, Admin), Web UI console, │ ed-iam-      │
│                        │ Docker image publishing        │ playground   │
└────────────────────────┴────────────────────────────────┴──────────────┘
```

---

## 2. Detailed Feature Breakdown

### 2.1 Multi-Tenant Core & Context Propagation
* **Pure Domain Representation**: `TenantId` (RFC 9562 UUIDv7) and `TenantOwned` interfaces prevent database leaking into domain models.
* **Tenant Auto-Resolution**: Users log in using email and password; single-tenant accounts are automatically bound to their tenant context.
* **Tenant Context Switching (`/api/v1/auth/switch-tenant`)**: Users with multi-tenant access receive `availableTenantIds` in their profile and can switch active tenants post-login.
* **Host Application Bridge (`TenantContextBridge`)**: Pluggable SPI hook enabling host applications to propagate tenancy context into their own `ScopedValue` or `ThreadLocal` systems during filter execution.
* **Namespaced Database Schema**: All tables use the `iam_*` prefix (`iam_user`, `iam_role`, `iam_group`, `iam_scope_node`), ensuring zero collisions with host application tables.

### 2.2 Declarative Security & Virtual-Thread-Safe Context
* **Java 25 `ScopedValue` Context**: `SecurityContextAccessor` binds the authenticated `CurrentActor` to the request call stack using Java 25 `ScopedValue`, eliminating memory leaks in virtual thread pools.
* **`@RequirePermission`**: Declarative Spring MVC controller security with `Logical.AND` and `Logical.OR` composition.
* **SpEL Security Evaluator (`@iam`)**: Custom bean evaluator for Spring Security method-level security (`@iam.hasPermission(...)`, `@iam.canAccessScope(...)`).
* **JJWT Token Engine**: High-throughput HMAC-SHA256 access and refresh token creation, claim verification, and signing.

### 2.3 Authentication SPI & Provider Router
* **Provider Routing Strategy**: Dynamic resolution of incoming credentials to matching `AuthenticationProvider` implementations.
* **Local Password Authentication**: Database-backed password verification using Spring Security BCrypt hashing.
* **Federated Identity & OIDC**: External token processing with automatic local user auto-provisioning and identity mapping (`iam_user_identity`).
* **Machine-to-Machine (M2M) API Keys**: `ApiKeyAuthProvider` automatically configured when an `ApiKeyValidatorPort` bean is present.

### 2.4 Hierarchical Scoping & Access Resolution
* **Path-Indexed Scope Trees (`ScopeNode`)**: Organizational hierarchies (Hospital $\to$ Clinic $\to$ Ward) modeled as path-indexed trees (`/root/parent/child/`) for immediate $O(1)$ ancestor and descendant authorization.
* **Role Inheritance**: Roles assigned to `Group` entities are dynamically inherited by all members.
* **`EffectiveAccessResolver`**: Single-pass resolution engine compiling direct roles, inherited group roles, permissions, and accessible scope boundaries into an immutable `EffectiveAccess` snapshot encoded in JWT claims.

### 2.5 Identity Management & REST Endpoints
* **User Management (`/api/v1/users`)**: User provisioning, lifecycle status updates (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`), dynamic criteria filtering, pagination (`page`, `size`, `sort`), and direct role/group assignments.
* **Role Management (`/api/v1/roles`)**: CRUD for tenant roles and permissions with safeguards protecting system roles.
* **Group Management (`/api/v1/groups`)**: CRUD for user groups, external IdP group mapping synchronization, and group-level role assignments.
* **Scope Management (`/api/v1/scopes`)**: Tree node creation, subtree traversal, and cyclical move prevention.

### 2.6 Token Revocation & Session Management (Phase 3 Accelerated)
* **Real-time Token Revocation (`TokenRevocationPort`)**: Immediate token invalidation via JWT ID (`jti`) denylisting checked at `JwtAuthenticationFilter`. Supports Redis cluster stores (`RedisTokenRevocationStore`) and zero-dependency in-memory stores (`InMemoryTokenRevocationStore`).
* **Concurrent Session Limits (`SessionRegistryPort`)**: Tracks active user sessions with IP addresses, user agents, creation, and last-access timestamps. Configurable policies enforce concurrent session thresholds using eviction (`TERMINATE_OLDEST`) or rejection (`REJECT_NEW`).
* **Brute-Force & Lockout Remediation (`LoginAttemptTrackerPort`)**: Tracks failed authentication attempts across identities and IP addresses, locking out brute-force attacks for configurable durations (`iam.security.session.lockout-duration-seconds`).
* **Self-Service Auth Endpoints (`/api/v1/auth/*`)**:
  * `POST /api/v1/auth/logout`: Revokes the calling actor's current session and token.
  * `POST /api/v1/auth/logout-all`: Invalidator terminating all concurrent sessions for the user.
  * `GET /api/v1/auth/sessions`: Inspects all currently active sessions belonging to the caller, highlighting the active token.
* **Administrative Management Endpoints (`/api/v1/users/*`)**:
  * `GET /api/v1/users/{id}/sessions` (`iam:session:read`): Administrative oversight of all sessions for a target user.
  * `DELETE /api/v1/users/{id}/sessions/{sessionId}` (`iam:session:delete`): Terminate a specific user session.
  * `DELETE /api/v1/users/{id}/sessions` (`iam:session:delete`): Bulk termination of all sessions for a user.
  * `GET /api/v1/users/{id}/lockout` (`iam:user:read`): Check failed login attempt count and lockout status.
  * `POST /api/v1/users/{id}/unlock` (`iam:user:update`): Instantly unlock an account and reset failed attempts.
* **Lifecycle Synchronization**: Automatic session termination and token revocation upon user account suspension (`SUSPENDED`/`DEACTIVATED`) or account deletion.

### 2.7 Multi-Factor Authentication (MFA / TOTP)
* **RFC 6238 Domain Engine (`TotpGenerator`)**: 100% pure Java implementation of HMAC-SHA1 time-based one-time password algorithm (30s step, 6 digits) with RFC 4648 Base32 encoding/decoding and clock drift tolerance ($\pm 1$ time step).
* **Two-Step Login Challenge Integration**: When MFA is activated for a user account, password authentication returns a transient, cryptographically signed `mfaChallengeToken` (300s TTL) with `mfaRequired: true`. Full access and refresh tokens are only granted upon successful TOTP verification at `/api/v1/auth/mfa/verify`.
* **Single-Use Backup Recovery Codes**: Enrollment generates 8 alphanumeric recovery codes (`XXXX-XXXX`) stored as salted SHA-256 hashes in `iam_user_mfa`. Each code can be consumed exactly once for emergency recovery and cannot be replayed.
* **MFA Self-Service REST Endpoints (`/api/v1/auth/mfa/*`)**:
  * `GET /api/v1/auth/mfa/status`: Inspect whether MFA is currently enabled.
  * `POST /api/v1/auth/mfa/setup`: Generates new Base32 secret, `otpauth://` QR URI, and backup recovery codes.
  * `POST /api/v1/auth/mfa/activate`: Confirms code verification before permanently enabling MFA.
  * `POST /api/v1/auth/mfa/verify`: Validates MFA challenge token using either TOTP code or backup code.
### 2.8 Observability, Distributed Tracing & Security Auditing (Phase 4)
* **OpenTelemetry Distributed Tracing & Native Metrics (`IamTelemetry`, `DefaultIamTelemetry`)**:
  * Distributed tracing spans: `iam.auth.authenticate`, `iam.token.validate`, and `iam.security.check` with standard OpenTelemetry attributes (`iam.tenant_id`, `iam.auth.type`, `iam.user_id`, `iam.auth.success`, `iam.client_ip`).
  * Micrometer metrics:
    * `iam.auth.latency`: Timer measuring authentication latency across auth providers.
    * `iam.token.validation.time`: Timer measuring JWT validation latency in `JwtAuthenticationFilter`.
    * `iam.auth.attempts`: Counter tracking login attempts tagged with outcome (`success`/`failure`) and auth type.
    * `iam.access.denied`: Counter tracking access denials tagged with reason (`missing_permission`, `invalid_token`, `expired_token`, `revoked_token`).
* **Security Audit Event Publishing (`EventPublisherPort`, `SecurityAuditEventListener`)**:
  * Standardized domain events (`IamEvent`) published for all security mutations and lifecycle transitions:
    * Authentication: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`
    * Sessions: `SESSION_CREATED`, `SESSION_REVOKED`, `SESSIONS_REVOKED_ALL`
    * Authorization: `ACCESS_DENIED`
    * Role Lifecycle: `ROLE_CREATED`, `ROLE_MODIFIED`, `ROLE_DELETED`
    * Group Lifecycle: `GROUP_CREATED`, `GROUP_UPDATED`, `GROUP_DELETED`
    * User Lifecycle: `USER_CREATED`, `USER_UPDATED`, `USER_STATUS_CHANGED`, `USER_DEACTIVATED`
    * Role Assignments: `ROLE_ASSIGNMENT_CREATED`, `ROLE_ASSIGNMENT_REVOKED`
  * Structured SLF4J audit logger (`io.github.edmaputra.iam.audit`) outputting machine-parseable log entries for SIEM integration with tenant, actor, entity, and payload details. Configurable via `iam.security.audit.logging-enabled` and `iam.security.audit.logger-name`.

---

## 3. Product Roadmap

```
  ┌────────────────────────────────────────────────────────┐
  │                 COMPLETED (v0.1.0 - v0.5.0)            │
  │  ✓ Hexagonal Domain Kernel (ed-iam-core)               │
  │  ✓ ScopedValue & Declarative Security (resource-server)│
  │  ✓ Pluggable Auth Router & EffectiveAccess (auth)      │
  │  ✓ JPA & Liquibase Schema Migrations (management)      │
  │  ✓ User Dynamic Filtering & Pagination                 │
  │  ✓ Interactive Multi-Tenant Clinical Playground        │
  │  ✓ Token Revocation & Session Management (Phase 3)     │
  │  ✓ RFC 6238 TOTP Multi-Factor Authentication (Phase 1) │
  │  ✓ Passwordless Magic Link Authentication (Phase 1)    │
  │  ✓ OpenTelemetry Tracing & Metrics (Phase 4)           │
  │  ✓ Security Audit Event Trail Publisher (Phase 4)      │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │                 PHASE 1: ENTERPRISE AUTH (IN PROGRESS) │
  │  - SAML 2.0 Web SSO Provider                           │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │                 PHASE 2: ATTRIBUTE-BASED ACCESS (ABAC) │
  │  - Dynamic Policy Evaluator                            │
  │  - Contextual Constraints (time-of-day, network CIDR)  │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │                 PHASE 5: DISTRIBUTION & DEVELOPER HUB  │
  │  - Automated Maven Central OSSRH Release Pipeline      │
  │  - Developer Documentation Portal                      │
  └────────────────────────────────────────────────────────┘
```

### Milestone Progress Tracker

#### Phase 0: Foundations & Modularization (Delivered in v0.1.0 – v0.3.0)
- [x] **Hexagonal Architecture**: Inward dependency flow enforced via ArchUnit.
- [x] **Java 25 ScopedValue**: Virtual-thread friendly `CurrentActor` security context.
- [x] **Modular Split**: Decoupled `core`, `resource-server`, `auth`, `management`, and `starter`.
- [x] **Declarative Security**: `@RequirePermission` and `@iam` SpEL evaluator.
- [x] **Authentication SPI**: Local BCrypt, OIDC federated linking, and M2M API Keys.
- [x] **Hierarchical Scope Trees**: Path-indexed `ScopeNode` with $O(1)$ subtree checks.
- [x] **User Pagination & Dynamic Filtering**: Spring Data JPA criteria queries on `/api/v1/users`.
- [x] **Isolated Migrations**: Self-contained `iam_*` changelogs via Liquibase.
- [x] **Interactive Playground**: Multi-tenant clinical demo (`samples/ed-iam-playground`) with Docker containerization.

#### Phase 1: Enterprise Authentication & MFA
- [ ] **SAML 2.0 Web SSO Provider**: Enterprise SAML assertion consumer service (ACS) for enterprise hospital and university SSO.
- [x] **Multi-Factor Authentication (MFA / TOTP)**: RFC 6238 time-based one-time password verification (Google Authenticator, Microsoft Authenticator), RFC 4648 Base32 secret generation, `otpauth://` QR URI provisioning, SHA-256 single-use recovery backup codes, and two-step login challenge flow (`/api/v1/auth/mfa/*`).
- [x] **Magic Link & Passwordless Provider**: One-time email token authentication flows, 256-bit cryptographically secure URL-safe tokens, atomic single-use invalidation, tenant-scoped session resolution, MFA interception, and interactive simulation (`/api/v1/auth/magic-link/*`).

#### Phase 2: Attribute-Based Access Control (ABAC)
- [ ] **Dynamic Policy Evaluator**: Flexible rule engine evaluating subject, resource, and contextual attributes.
- [ ] **Contextual Policy Constraints**: Access policies constrained by time-of-day, network CIDR subnets, and device posture.

#### Phase 3: Token Revocation & Session Management (Accelerated & Delivered)
- [x] **Distributed & In-Memory Token Revocation**: Cluster-wide Redis token denylist (`RedisTokenRevocationStore`) and fallback in-memory store (`InMemoryTokenRevocationStore`) with token validation hook in `JwtAuthenticationFilter`.
- [x] **Concurrent Session Limits**: Configurable policies enforcing max concurrent active sessions per user account (`TERMINATE_OLDEST` eviction or `REJECT_NEW`), tracking client IP, user-agent, creation, and last access timestamps (`SessionProperties`, `SessionRegistryPort`).
- [x] **Brute-Force & Account Lockout**: Built-in IP and identity lockout mechanisms with pluggable rate-limiting stores (`InMemoryLoginAttemptTracker` and `RedisLoginAttemptTracker`).
- [x] **Self-Service REST Endpoints**: `/api/v1/auth/logout`, `/api/v1/auth/logout-all`, and `/api/v1/auth/sessions`.
- [x] **Administrative Management Endpoints**: `/api/v1/users/{id}/sessions`, `/api/v1/users/{id}/sessions/{sessionId}`, `/api/v1/users/{id}/sessions`, `/api/v1/users/{id}/lockout`, and `/api/v1/users/{id}/unlock`.
- [x] **Lifecycle Synchronization**: Automatic session termination and token revocation upon user account suspension (`SUSPENDED`/`DEACTIVATED`) or account deletion.

#### Phase 4: Observability & Security Auditing
- [x] **OpenTelemetry Metrics & Tracing**: Native metrics for authentication latency (`iam.auth.latency`), token validation times (`iam.token.validation.time`), login attempts (`iam.auth.attempts`), and access denial counters (`iam.access.denied`), along with OpenTelemetry distributed tracing spans (`iam.auth.authenticate`, `iam.token.validate`, `iam.security.check`).
- [x] **Security Audit Event Publisher**: Standardized Spring application event publication (`IamEvent`) via `EventPublisherPort` for all security mutations and lifecycle transitions (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `ROLE_CREATED`, `ROLE_MODIFIED`, `ROLE_DELETED`, `USER_CREATED`, `USER_UPDATED`, `USER_STATUS_CHANGED`, `USER_DEACTIVATED`, `GROUP_CREATED`, `GROUP_UPDATED`, `GROUP_DELETED`, `ROLE_ASSIGNMENT_CREATED`, `ROLE_ASSIGNMENT_REVOKED`, `SESSION_CREATED`, `SESSION_REVOKED`, `SESSIONS_REVOKED_ALL`, `ACCESS_DENIED`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`), with structured SLF4J audit logging (`SecurityAuditEventListener`).

#### Phase 5: Distribution & Developer Experience
- [x] **GitHub Actions Automated CI/CD**: Automated testing across Java 25.
- [ ] **Automated Maven Central Publication**: GPG-signed artifact deployment to Maven Central via Sonatype Central Portal.
- [ ] **Interactive OpenAPI & Swagger UI**: Auto-generated documentation for all `/api/v1/*` endpoints.
