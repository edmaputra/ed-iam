# 03. Current Features & Product Roadmap

This document outlines the operational capabilities available in the current release (**v0.3.0**) and defines the forward-looking roadmap for future iterations of `ed-iam`.

---

## 1. Current Capabilities Matrix (v0.3.0)

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

---

## 3. Product Roadmap

```
  ┌────────────────────────────────────────────────────────┐
  │                 COMPLETED (v0.1.0 - v0.3.0)            │
  │  ✓ Hexagonal Domain Kernel (ed-iam-core)               │
  │  ✓ ScopedValue & Declarative Security (resource-server)│
  │  ✓ Pluggable Auth Router & EffectiveAccess (auth)      │
  │  ✓ JPA & Liquibase Schema Migrations (management)      │
  │  ✓ User Dynamic Filtering & Pagination                 │
  │  ✓ Interactive Multi-Tenant Clinical Playground        │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │                 PHASE 1: ENTERPRISE AUTH & MFA         │
  │  - SAML 2.0 Web SSO Provider                           │
  │  - RFC 6238 TOTP Multi-Factor Authentication           │
  │  - Passwordless Magic Link Provider                    │
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
  │                 PHASE 3: TOKEN REVOCATION & SESSIONS   │
  │  - Distributed Redis-backed Token Revocation           │
  │  - Concurrent Session Limits & Brute-Force Protection  │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │                 PHASE 4: OBSERVABILITY & AUDIT         │
  │  - OpenTelemetry Distributed Tracing & Metrics         │
  │  - Security Event Audit Trail Publisher                │
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
- [ ] **Multi-Factor Authentication (MFA / TOTP)**: RFC 6238 time-based one-time password verification (Google Authenticator, Microsoft Authenticator).
- [ ] **Magic Link & Passwordless Provider**: One-time email token authentication flows.

#### Phase 2: Attribute-Based Access Control (ABAC)
- [ ] **Dynamic Policy Evaluator**: Flexible rule engine evaluating subject, resource, and contextual attributes.
- [ ] **Contextual Policy Constraints**: Access policies constrained by time-of-day, network CIDR subnets, and device posture.

#### Phase 3: Token Revocation & Session Management
- [ ] **Distributed Token Revocation**: Redis-backed token revocation list for immediate session termination on logout or credential change.
- [ ] **Concurrent Session Limits**: Configurable policies enforcing max concurrent active sessions per user account.
- [ ] **Brute-Force & Rate Limiting**: Built-in IP and account lockout mechanisms with pluggable rate-limiting stores.

#### Phase 4: Observability & Security Auditing
- [ ] **OpenTelemetry Metrics & Tracing**: Native metrics for authentication latency, token validation times, and access denial counters.
- [ ] **Security Audit Event Publisher**: Standardized Spring application event publication for all security mutations (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `ROLE_MODIFIED`, `SCOPE_MOVED`).

#### Phase 5: Distribution & Developer Experience
- [x] **GitHub Actions Automated CI/CD**: Automated testing across Java 25.
- [ ] **Automated Maven Central Publication**: GPG-signed artifact deployment to Maven Central via Sonatype Central Portal.
- [ ] **Interactive OpenAPI & Swagger UI**: Auto-generated documentation for all `/api/v1/*` endpoints.
