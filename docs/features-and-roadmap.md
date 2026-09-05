# Features and Roadmap: `ed-iam` (IAM Spring Boot Starter)

`ed-iam` is a modular, pluggable Identity & Access Management (IAM) starter built for **Java 25** and **Spring Boot 4.x**. It provides zero-boilerplate multi-tenancy, Role-Based Access Control (RBAC), hierarchical organizational scoping, and JWT-based authentication context.

---

## 1. Current Features (v1.0.0-SNAPSHOT)

### 1.1 Multi-Tenant Core Architecture
- **Isolated Tenancy Model**: Pure domain representation via `TenantId` (RFC 9562 UUIDv7) and `TenantOwned` interfaces without coupling to host application databases.
- **Zero-Config Login & Tenant Auto-Resolution**: Users log in with standard `email` and `password` without entering a tenant UUID; single-tenant users are automatically resolved and bound to their tenant context by `EffectiveAccessResolver`.
- **Multi-Tenant Switching (`/api/v1/auth/switch-tenant`)**: Multi-tenant users receive their complete list of `availableTenantIds` in their profile, enabling post-login tenant context switching via `POST /api/v1/auth/switch-tenant`.
- **Flexible Header & Body Support**: Optional `X-Tenant-ID` HTTP header support across login and tenant-switching endpoints with automatic UUID validation and request body fallback.
- **Pluggable Host Bridge (`TenantContextBridge`)**: A functional SPI hook enabling consuming applications to propagate their own request-scoped tenancy contexts (`ScopedValue`, `ThreadLocal`) automatically during filter execution.
- **Database Schema Namespacing**: All database tables are isolated with the `iam_*` prefix (`iam_user`, `iam_role`, `iam_group`, `iam_scope_node`, etc.) with zero foreign-key hardcoded dependencies on host tables.

### 1.2 Authentication SPI & Providers
- **Authentication Provider Router**: Pluggable `AuthenticationProvider` SPI allowing arbitrary authentication strategies to be resolved dynamically based on request credentials.
- **Local Password Provider**: Database-backed authentication with BCrypt password hashing.
- **OIDC / Federated Identity Provider**: External OpenID Connect identity linking with automatic local user provisioning and federated identity mapping (`iam_user_identity`).
- **Machine-to-Machine (M2M) API Key Provider**: Dedicated SPI provider (`ApiKeyAuthProvider`) auto-configured conditionally when an `ApiKeyValidatorPort` bean is present.
- **Extensible SPI**: Clean port definitions for easily adding new authentication providers (LDAP, SAML, Magic Link, API Keys).

### 1.3 Role-Based Access Control & Hierarchical Scoping
- **Granular Permissions & Roles**: Flexible role definitions mapping to granular permission strings (e.g. `PATIENT_READ`, `BILLING_WRITE`).
- **User Groups & Role Inheritance**: Users can belong to multiple groups; roles assigned to groups are dynamically inherited by members.
- **Hierarchical Scope Trees (`ScopeNode`)**: Organizational hierarchies (e.g., Hospital -> Clinic -> Department -> Ward) modeled as path-indexed trees.
- **Scope Subtree Resolution**: High-performance subtree and ancestor authorization checks (`CurrentActor.canAccessScope(nodeId)`).
- **Effective Access Resolver**: Single-pass resolution engine compiling a user's combined direct roles, group roles, wildcard permissions, and accessible scope boundaries into an immutable `EffectiveAccess` snapshot.

### 1.4 Security Context & Token Engine
- **HMAC-SHA256 Token Provider**: High-throughput JWT access and refresh token generation, signing, and claim verification using `io.jsonwebtoken (jjwt 0.12.x)`.
- **Java 25 `ScopedValue` Context**: Non-blocking, virtual-thread friendly actor propagation via `SecurityContextAccessor` implementing `CurrentActorProvider`.
- **`JwtAuthenticationFilter`**: Standard Spring `OncePerRequestFilter` extracting Bearer tokens, verifying signatures, establishing the `CurrentActor` scope, and coordinating with `TenantContextBridge`.
- **Configuration Autocomplete**: Auto-generated metadata (`META-INF/spring-configuration-metadata.json`) providing IDE code completion for `iam.jwt.*` properties.

### 1.5 Database Migrations
- **Isolated Liquibase Integration**: Self-contained changelog runner (`db.changelog-iam.json`) that safely executes IAM table setup without conflicting with host application migrations.

### 1.6 Modular Architecture
- **`ed-iam-core`**: Pure domain models (`User`, `Role`, `Group`, `ScopeNode`), domain events, ports, and invariants with zero Spring or database dependencies.
- **`ed-iam-resource-server`**: Lightweight downstream library containing JWT parsing, `JwtAuthenticationFilter`, and `ScopedValue` context binding without JPA/Liquibase overhead.
- **`ed-iam-management`**: Complete administrative management library containing persistence adapters, domain services, security provider configurations, Liquibase auto-configuration, and REST endpoints.
- **`ed-iam-starter`**: Convenience aggregator starter bundling core, resource-server, and management modules.

### 1.7 Dedicated Administrative Management Endpoints
- **User Management (`/api/v1/users`)**: Provisioning, status updates (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`), direct role assignments, and group memberships.
- **Role Management (`/api/v1/roles`)**: CRUD for custom tenant roles and permission definitions with system role deletion safeguards.
- **Group Management (`/api/v1/groups`)**: CRUD for user groups, external IdP group mapping synchronization, and group-level role assignments.
- **Scope Management (`/api/v1/scopes`)**: Hierarchical tree management, child node creation, and subtree reparenting/moves.
- **Endpoint Toggle (`iam.management.endpoints.enabled`)**: Configurable property (defaults to `true`) allowing host applications to disable REST controllers while preserving domain use-case beans.

---

## 2. Product Roadmap

```
  ┌───────────────────────┐     ┌───────────────────────┐     ┌───────────────────────┐
  │        Phase 1        │     │        Phase 2        │     │        Phase 3        │
  │  Multi-Provider &     │ ──► │  ABAC & Method-Level  │ ──► │  Starter Split:       │
  │  MFA Support          │     │  Security Extensions  │     │  Server vs Client     │
  └───────────────────────┘     └───────────────────────┘     └───────────────────────┘
              │                             │                             │
              ▼                             ▼                             ▼
  ┌───────────────────────┐     ┌───────────────────────┐     ┌───────────────────────┐
  │        Phase 4        │     │        Phase 5        │     │        Phase 6        │
  │  Distributed Cache &  │ ──► │  OpenTelemetry &      │ ──► │  Central Release &    │
  │  Token Revocation     │     │  Security Auditing    │     │  Developer Portal     │
  └───────────────────────┘     └───────────────────────┘     └───────────────────────┘
```

### Phase 1: Authentication Expansion & MFA
- [ ] **SAML 2.0 Web SSO Provider**: Enterprise SAML assertion processing for hospital and university single sign-on.
- [ ] **API Key / Machine-to-Machine (M2M) Provider**: Dedicated service account authentication with scope constraints and expiration.
- [ ] **Multi-Factor Authentication (MFA/TOTP)**: RFC 6238 TOTP verification (Google Authenticator, Microsoft Authenticator) for sensitive administrative roles.
- [ ] **Magic Link & Passwordless Provider**: One-time email token authentication flows.

### Phase 2: Method-Level Security & ABAC
- [ ] **Spring Security Expression Bridge**: Custom SpEL evaluators enabling declarative method-level annotations:
  ```java
  @PreAuthorize("@iam.hasPermission('CLINICAL_WRITE') and @iam.canAccessScope(#departmentId)")
  public void updateChart(UUID departmentId, ChartDto chart) { ... }
  ```
- [ ] **Attribute-Based Access Control (ABAC)**: Dynamic policy evaluator assessing contextual attributes (time of day, network subnet, resource ownership).

### Phase 3: Starter Modularization (Resource Server vs. Auth Server)
- [ ] **`ed-iam-core`**: Pure interfaces, domain contracts (`CurrentActor`, `TenantId`), and exception models.
- [ ] **`ed-iam-resource-server-starter`**: Lightweight starter for downstream microservices containing only the `JwtAuthenticationFilter`, token validation, and context binding — **zero database or Liquibase dependency**.
- [ ] **`ed-iam-server-starter`**: Full identity management starter containing persistence, JPA repositories, user administration use cases, and auth endpoints.

### Phase 4: Token Revocation & Session Invalidation
- [ ] **Distributed Token Blacklisting**: Optional Redis-backed token revocation list for immediate session termination on logout or password reset.
- [ ] **Concurrent Session Limiting**: Policy enforcement for maximum active sessions per user account.
- [ ] **Brute-Force & Rate Limiting**: Built-in IP and account lockout mechanisms with pluggable rate-limit storage.

### Phase 5: Observability & Security Auditing
- [ ] **Audit Trail Event Publisher**: Spring application events emitted for every security event (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `ROLE_MODIFIED`, `SCOPE_MOVED`).
- [ ] **OpenTelemetry Metrics & Tracing**: Pre-instrumented authentication timing, token validation latencies, and security failure counters.

### Phase 6: Release Engineering & Open Source Distribution
- [ ] **GitHub Actions CI/CD**: Automated matrix testing across Java 25 versions.
- [ ] **Maven Central Publication**: Automated signing (GPG) and publishing via Central Portal / Sonatype OSSRH.
- [ ] **Interactive Playground Sample App**: Example multi-tenant Spring Boot reference application demonstrating common usage patterns.
