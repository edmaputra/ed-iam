# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.8.0] - 2026-10-04

### Security
- **JWT Cryptographic Hardening & Claim Validation**:
  - Enforced mandatory, non-blank secret key with a minimum 256-bit (32 bytes) requirement for HMAC-SHA256 in `JwtProperties`, eliminating insecure default secret fallback ([#23](https://github.com/edmaputra/ed-iam/pull/23)).
  - Added configurable token issuer (`iss`) and audience (`aud`) verification with safe defaults (`ed-iam`, `ed-iam-api`), enforcing validation during token parsing in `JwtTokenProvider` ([#23](https://github.com/edmaputra/ed-iam/pull/23)).
  - Embedded unique token identifier (`jti`), `iss`, and `aud` claims across access tokens, refresh tokens, and MFA challenge tokens ([#23](https://github.com/edmaputra/ed-iam/pull/23)).
  - Escaped control characters, quotes, and backslashes in unauthorized error JSON responses within `JwtAuthenticationFilter` to prevent JSON injection ([#24](https://github.com/edmaputra/ed-iam/pull/24)).
- **Account Enumeration & Timing Side-Channel Defense**:
  - Implemented constant-time password verification in `LocalPasswordAuthProvider` using a pre-computed dummy BCrypt hash (`DUMMY_BCRYPT_HASH`) when user does not exist or has no local password hash, defeating timing side-channel probes ([#26](https://github.com/edmaputra/ed-iam/pull/26)).
  - Standardized generic `"Invalid credentials."` error responses across missing user, missing password hash, and invalid password scenarios to eliminate account enumeration ([#26](https://github.com/edmaputra/ed-iam/pull/26)).
  - Deferred account status verification (`isSuspended()`, `isDeactivated()`) until after credentials are authenticated, preventing attackers from probing account states without valid credentials ([#26](https://github.com/edmaputra/ed-iam/pull/26)).
- **Refresh Token Rotation & Reuse Detection**:
  - Implemented single-use Refresh Token Rotation (RTR) revoking consumed refresh tokens upon new token pair issuance ([#25](https://github.com/edmaputra/ed-iam/pull/25)).
  - Added token reuse detection in `AuthenticationService`: replaying an already-revoked refresh token immediately invalidates all active sessions for the user (`logoutAll`) and rejects the request ([#25](https://github.com/edmaputra/ed-iam/pull/25)).
- **Cross-Tenant Authorization & Privilege Escalation Guards**:
  - Enforced tenant boundary checks across `UserManagementService`, `GroupManagementService`, `RoleManagementService`, and `ScopeHierarchyService` via `CurrentActorProvider`, blocking cross-tenant entity modification or access by non-superadmins ([#25](https://github.com/edmaputra/ed-iam/pull/25)).
  - Restricted platform superadmin user creation exclusively to existing platform superadmins in `UserManagementService.createUser()` ([#24](https://github.com/edmaputra/ed-iam/pull/24)).
  - Blocked modification and deletion of immutable system roles in `RoleManagementService` ([#24](https://github.com/edmaputra/ed-iam/pull/24)).
- **Input Bounding & Information Disclosure Protection**:
  - Enforced 128-character maximum length constraint on passwords in `LoginRequest` and `UserManagementDtos.CreateUserRequest` to protect BCrypt hashing routines against resource exhaustion / DoS attacks ([#24](https://github.com/edmaputra/ed-iam/pull/24)).
  - Masked rejected values for sensitive fields (`password`, `secret`, `token`, `apikey`, `credential`, `privatekey`) with `[PROTECTED]` in validation error responses within `IamExceptionHandler` ([#24](https://github.com/edmaputra/ed-iam/pull/24)).
  - Added centralized catch-all handler in `IamExceptionHandler` returning generic RFC 9457 HTTP 500 Problem Details to prevent internal exception leakage ([#24](https://github.com/edmaputra/ed-iam/pull/24)).
- **Authentication & Security Audit Logging**:
  - Added structured SLF4J security audit logs in `AuthenticationService` and `LocalPasswordAuthProvider` for successful logins, failed attempts, account lockouts, suspended/deactivated account rejections, and refresh token reuse events ([#26](https://github.com/edmaputra/ed-iam/pull/26)).

### Added
- Added `tokenId` property and `optionalTokenId()` accessor to `RefreshTokenClaims` in `ed-iam-core` ([#23](https://github.com/edmaputra/ed-iam/pull/23)).
- Added Spring Security `SecurityFilterChain` integration guide and architecture best practices in `docs/walkthrough/modules/02-resource-server.md` ([#27](https://github.com/edmaputra/ed-iam/pull/27)).
- Added explicit `@Transactional` and `@Transactional(readOnly = true)` annotations across management services to ensure transactional consistency ([#25](https://github.com/edmaputra/ed-iam/pull/25)).
- Added environment variable overrides (`IAM_JWT_SECRET`, `IAM_JWT_ISSUER`, `IAM_JWT_AUDIENCE`) and security advisory notes in Playground configuration ([#23](https://github.com/edmaputra/ed-iam/pull/23)).

---

## [0.7.0] - 2026-10-02

### Added
- **Passwordless Magic Link Authentication (Phase 1)**:
  - Cryptographically secure 256-bit URL-safe token generator (`SecureRandom`) and domain model (`MagicLinkToken`, `MagicLinkId`, `MagicLinkAuthCredentials`).
  - Single-use atomic invalidation in JPA persistence (`JpaMagicLinkTokenStoreAdapter`, `MagicLinkTokenJpaEntity`, `MagicLinkTokenJpaRepository`) preventing race conditions and replay attacks, with fallback `InMemoryMagicLinkTokenStore`.
  - Multi-tenant context preservation linking tokens to optional `TenantId` and resolving tenant-scoped effective access and permissions upon verification.
  - Multi-factor authentication interception issuing `MfaChallengeToken` when user has active TOTP MFA enrolled.
  - Dedicated Liquibase migration changelog `2026092902-create-iam-magic-link-token.json` creating `iam_magic_link_token` table with index.
  - Public REST endpoints in `MagicLinkController` (`/api/v1/auth/magic-link/*`):
    - `POST /api/v1/auth/magic-link/request`: Dispatches one-time magic link token with optional tenant and redirect destination.
    - `POST /api/v1/auth/magic-link/verify`: Validates and atomically consumes magic link token, returning JWT access/refresh token pair.
    - `GET /api/v1/auth/magic-link/verify`: Direct browser verification endpoint supporting query-parameter tokens and client redirects.
  - Pluggable outbound dispatch SPI `MagicLinkNotifierPort` with default `LoggingMagicLinkNotifier` and simulated local inbox (`PlaygroundSimulatedMailService`).
  - Interactive Playground UI panel in `samples/ed-iam-playground` enabling one-click link generation, console verification URL display, and browser simulation.
  - Dynamic database-driven Scope Hierarchy Tree and access status evaluator in playground via `PlaygroundScopeController` (`GET /api/v1/playground/scopes/hierarchy`, `POST /api/v1/playground/scopes/evaluate`).
  - Replaced browser `alert()` popups with non-blocking floating toast notification system (`showToast`) in playground.
  - End-to-end integration tests in `ed-iam-management` (`MagicLinkAuthenticationIT`) and `ed-iam-playground` verifying complete passwordless request, token verification, single-use invalidation, and non-existent email handling.
  - Sample HTTP requests added to `samples/ed-iam-playground/playground-requests.http`.

### Changed
- Decoupled REST controllers in `ed-iam-auth` by separation of concerns:
  - Core authentication remains in `AuthController` (`/api/v1/auth/*`).
  - Multi-factor authentication extracted to dedicated `MfaController` (`/api/v1/auth/mfa/*`).
  - Magic Link authentication extracted to dedicated `MagicLinkController` (`/api/v1/auth/magic-link/*`).

---

## [0.6.0] - 2026-10-01

### Changed
- Upgraded GitHub Actions workflow configurations to execute under Node.js 24 runtime (`actions/checkout@v7`, `actions/upload-artifact@v7`, `crazy-max/ghaction-import-gpg@v7`), eliminating Node.js 20 deprecation warnings across all CI/CD pipelines ([#20](https://github.com/edmaputra/ed-iam/pull/20)).
- Upgraded ArchUnit dependencies to 1.5.1 to support Java 25 bytecode class file versions ([#20](https://github.com/edmaputra/ed-iam/pull/20)).

---

## [0.5.0] - 2026-09-29

### Added
- **RFC 6238 TOTP Multi-Factor Authentication (MFA) (Phase 1)**:
  - Pure Java RFC 6238 TOTP engine (`TotpGenerator`) implementing HMAC-SHA1 (30s step, 6 digits), RFC 4648 Base32 encoding/decoding, drift window verification ($\pm 1$ time step), and standard `otpauth://` QR URI generation.
  - Domain aggregate `UserMfa` maintaining Base32 secret, activation status, and single-use hashed recovery backup codes (`consumeBackupCode()`).
  - Liquibase changelog migration creating `iam_user_mfa` table and JPA persistence adapter (`UserMfaRepositoryAdapter`, `UserMfaJpaEntity`, `UserMfaJpaRepository`).
  - Two-step login challenge flow in `AuthenticationService` issuing transient cryptographically signed `mfaChallengeToken` (300s TTL) with `mfaRequired: true`.
  - Self-service MFA REST endpoints in `MfaController` (`/api/v1/auth/mfa/*`):
    - `GET /api/v1/auth/mfa/status`: Check MFA activation status.
    - `POST /api/v1/auth/mfa/setup`: Generate Base32 secret, `otpauth://` QR URI, and 8 single-use backup recovery codes.
    - `POST /api/v1/auth/mfa/activate`: Confirm and activate MFA with valid TOTP code.
    - `POST /api/v1/auth/mfa/verify`: Complete login challenge with TOTP code or backup recovery code.
    - `POST /api/v1/auth/mfa/disable`: Disable MFA with TOTP code or user account password.
  - User lifecycle cleanup cascade deleting associated MFA configuration upon user deletion in `UserManagementService`.
  - End-to-end integration test in `ed-iam-playground` verifying complete enrollment, login challenge, TOTP/backup code verification, backup code single-use consumption, replay prevention, and disabling.
  - Sample HTTP requests added to `samples/ed-iam-playground/playground-requests.http`.

---

## [0.4.0] - 2026-09-28

### Added
- **Token Revocation & Session Management (Phase 3)**:
  - Real-time token revocation via `TokenRevocationPort` with cluster-wide Redis (`RedisTokenRevocationStore`) and fallback in-memory (`InMemoryTokenRevocationStore`) stores.
  - Active session tracking and concurrency limiting via `SessionRegistryPort` with configurable eviction (`TERMINATE_OLDEST`) or rejection (`REJECT_NEW`) policies (`SessionProperties`).
  - Brute-force account lockout tracking via `LoginAttemptTrackerPort` with Redis and in-memory stores.
  - Token revocation validation hook in `JwtAuthenticationFilter` with immediate 401 Unauthorized rejection for revoked tokens.
  - Self-service authentication REST endpoints: `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all`, and `GET /api/v1/auth/sessions`.
  - Administrative oversight REST endpoints: `GET /api/v1/users/{id}/sessions`, `DELETE /api/v1/users/{id}/sessions/{sessionId}`, `DELETE /api/v1/users/{id}/sessions`, `GET /api/v1/users/{id}/lockout`, and `POST /api/v1/users/{id}/unlock`.
  - User lifecycle synchronization terminating active sessions upon user suspension (`SUSPENDED`/`DEACTIVATED`) or account deletion.

---

## [0.3.0] - 2026-09-24

### Added
- **Decoupled Authentication Module (`ed-iam-auth`)**:
  - Extracted authentication workflows, credential provider router, and JWT issuance into dedicated `ed-iam-auth` module ([#14](https://github.com/edmaputra/ed-iam/pull/14), [#13](https://github.com/edmaputra/ed-iam/pull/13)).
  - Relocated `AuthController` (`/api/v1/auth/*`), `AuthenticationService`, and `EffectiveAccessResolver` to `ed-iam-auth`.
  - Added `ed-iam-auth` to aggregator `ed-iam-starter` and Dockerfile build stages.
- **User Pagination & Filtering Endpoint**:
  - Added paginated user lookup with dynamic criteria filtering (`page`, `size`, `sort`, `email`, `status`) in `ManageUserUseCase` and `UserController` (`GET /api/v1/users`) ([#13](https://github.com/edmaputra/ed-iam/pull/13)).

### Changed
- Hardened hexagonal architecture boundaries, security adapters, and persistence models ([#12](https://github.com/edmaputra/ed-iam/pull/12)).
- Cleaned up unused dependencies, annotations, and null-safety warnings across all modules ([#14](https://github.com/edmaputra/ed-iam/pull/14)).

---

## [0.2.0] - 2026-09-15

### Added
- **Multi-Tenant Audit Context & Actor Categorization**:
  - Introduced `ActorType` enum (`USER`, `SYSTEM`, `MACHINE`) in domain context to distinguish caller identity types across audit and domain layers ([#9](https://github.com/edmaputra/ed-iam/pull/9)).
  - Enhanced `OperationContext` with `actorType` and optional `TenantId` support for richer multi-tenant scoping and audit trail generation ([#9](https://github.com/edmaputra/ed-iam/pull/9)).
  - Added ergonomic static factory methods `OperationContext.user(...)`, `OperationContext.system(...)`, and `OperationContext.machine(...)` with tenant ID and correlation ID overloads while maintaining backward compatibility for single-actor callers ([#9](https://github.com/edmaputra/ed-iam/pull/9)).
  - Added accessor methods `actorType()`, `tenantId()`, `optionalTenantId()`, and `optionalTenantUuid()` to `OperationContext` ([#9](https://github.com/edmaputra/ed-iam/pull/9)).
  - Added unit test coverage for `OperationContext` and `ActorType` invariants and validation rules ([#9](https://github.com/edmaputra/ed-iam/pull/9)).
- **Documentation & Standards**:
  - Standardized `CHANGELOG.md` adhering to Keep a Changelog 1.1.0 and Semantic Versioning 2.0.0 specifications ([#8](https://github.com/edmaputra/ed-iam/pull/8)).
  - Enhanced project `README.md` with modular starter guides, declarative `@RequirePermission` examples, and SpEL `@iam` evaluator usage ([#8](https://github.com/edmaputra/ed-iam/pull/8)).

### Changed
- Aligned Javadoc `@since` tags across all core, management, and resource server classes to `0.0.1` to match the initial release baseline ([#9](https://github.com/edmaputra/ed-iam/pull/9)).
- Upgraded GitHub Actions workflow dependencies across `ci.yml`, `deploy-sample.yml`, and `release.yml` (`actions/checkout@v7`, `actions/upload-artifact@v7`, `crazy-max/ghaction-import-gpg@v7`) to run natively on Node.js 24 runtime, resolving runner deprecation warnings.
- Configured JaCoCo multi-module report aggregation in `ed-iam-starter` and added unified coverage reporting directly in GitHub Actions Job Summary and pull request comments.

---

## [0.1.0] - 2026-09-13

### Added
- **Declarative Endpoint-Level Security**:
  - Introduced `@RequirePermission` annotation with logical operators (`Logical.AND`, `Logical.OR`) for fine-grained authorization on REST controllers ([#7](https://github.com/edmaputra/ed-iam/pull/7)).
  - Implemented `RequirePermissionInterceptor` Spring MVC handler interceptor for automatic permission validation on annotated controller methods and handler types ([#7](https://github.com/edmaputra/ed-iam/pull/7)).
  - Added `IamSecurityEvaluator` bean (`@iam`) exposing SpEL evaluation helpers (`hasPermission`, `hasAnyPermission`, `canAccessScope`, `getCurrentActor`) ([#7](https://github.com/edmaputra/ed-iam/pull/7)).
  - Enabled `@RequirePermission` annotations across all entity management controllers (`UserController`, `RoleController`, `GroupController`, `ScopeController`) ([#7](https://github.com/edmaputra/ed-iam/pull/7)).
- **Interactive Playground Enhancements**:
  - Added custom credentials login panel with dynamic token inspection and verification flow in `ed-iam-playground` ([#7](https://github.com/edmaputra/ed-iam/pull/7)).
  - Integrated official brand icons into the playground navbar and UI templates ([#5](https://github.com/edmaputra/ed-iam/pull/5)).
- **Containerization & CI/CD**:
  - Multi-stage Dockerfile and Docker Compose setup for `ed-iam-playground` sample application ([#4](https://github.com/edmaputra/ed-iam/pull/4)).
  - Automated GitHub Actions pipeline to publish playground container images to GitHub Container Registry (`ghcr.io/edmaputra/ed-iam-playground`) ([#4](https://github.com/edmaputra/ed-iam/pull/4)).
- **Branding Assets**:
  - Vector brand icon (`ed-iam-icon.svg`) featuring ED monogram padlock and shield silhouette with gradient styling ([#5](https://github.com/edmaputra/ed-iam/pull/5)).
  - High-contrast black-and-white favicon variants (`favicon-bw.svg`, `favicon.ico`, PNG variants) ([#5](https://github.com/edmaputra/ed-iam/pull/5)).
- **Architecture Quality Gates**:
  - Added ArchUnit tests (`DomainArchitectureTest`, `ArchitectureTest`) enforcing hexagonal architecture, layer separation, and naming conventions ([#6](https://github.com/edmaputra/ed-iam/pull/6)).
  - Defined explicit outbound ports (`TokenProviderPort`, `EventPublisherPort`) for modular security decoupling ([#6](https://github.com/edmaputra/ed-iam/pull/6)).

### Changed
- Standardized package and class structure to align with hexagonal architecture conventions (`port.in`, `port.out`) ([#6](https://github.com/edmaputra/ed-iam/pull/6)).
- Updated validation exception handling to return HTTP 422 Unprocessable Content for blank token requests ([#6](https://github.com/edmaputra/ed-iam/pull/6)).

---

## [0.0.1] - 2026-09-08

### Added
- **Multi-Tenant Core Architecture**:
  - Pure domain representation of multi-tenancy via `TenantId` (UUIDv7) and `TenantOwned` contracts.
  - Zero-config login with automatic tenant resolution for single-tenant users.
  - Post-login tenant context switching endpoint (`POST /api/v1/auth/switch-tenant`).
  - Flexible `X-Tenant-ID` header and JSON request body tenant resolution.
  - Pluggable `TenantContextBridge` SPI for propagating tenant context to host applications.
  - Namespaced database schema isolation (`iam_*` tables) using Liquibase migrations.
- **Authentication SPI & Providers**:
  - Extensible `AuthenticationProvider` router SPI.
  - Local database credentials provider with BCrypt password hashing.
  - OpenID Connect (OIDC) / OAuth2 federated identity provider with automatic user provisioning.
  - Machine-to-Machine (M2M) API Key authentication provider (`ApiKeyAuthProvider`).
- **Role-Based Access Control (RBAC) & Scoping**:
  - Granular permissions and flexible role assignments.
  - User groups with dynamic role inheritance.
  - Path-indexed hierarchical scope trees (`ScopeNode`) for modeling organizational boundaries.
  - High-performance subtree and ancestor authorization checks (`CurrentActor.canAccessScope(nodeId)`).
  - Single-pass `EffectiveAccessResolver` compiling roles, groups, and scope boundaries into immutable snapshots.
- **JWT Engine & Security Context**:
  - High-throughput HMAC-SHA256 JWT access and refresh token generation and verification using `io.jsonwebtoken (jjwt 0.12.x)`.
  - Non-blocking, virtual-thread friendly actor propagation via Java 25 `ScopedValue` context (`CurrentActorProvider`).
  - `JwtAuthenticationFilter` with Bearer token parsing and tenant context propagation.
  - Spring Boot configuration metadata for IDE auto-completion of `iam.jwt.*` properties.
- **Modular Project Structure**:
  - `ed-iam-core`: Pure domain models, events, and ports with zero framework coupling.
  - `ed-iam-resource-server`: Lightweight library with JWT validation, `JwtAuthenticationFilter`, and `ScopedValue` context binding.
  - `ed-iam-management`: Persistence adapters, domain services, Liquibase auto-configuration, and REST endpoints.
  - `ed-iam-starter`: Convenient aggregator starter bundling core, resource server, and management modules.
- **Dedicated Administrative Management Endpoints**:
  - User management endpoints (`/api/v1/users`).
  - Role management endpoints (`/api/v1/roles`).
  - Group management endpoints (`/api/v1/groups`).
  - Scope hierarchy management endpoints (`/api/v1/scopes`).
  - Configurable toggle (`iam.management.endpoints.enabled`) to disable default controllers.
- **Testing & Verification**:
  - Live HTTP integration test suite using `WebTestClient` across authentication, multi-tenancy, and administration flows.
  - JaCoCo test coverage reporting maintaining high branch coverage across modules.
- **Interactive Playground**:
  - `samples/ed-iam-playground` reference application demonstrating multi-tenant authentication, scope navigation, and user management.
- **Automated CI/CD**:
  - GitHub Actions automated release pipeline publishing signed artifacts to Maven Central via Sonatype Central Portal.

[Unreleased]: https://github.com/edmaputra/ed-iam/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/edmaputra/ed-iam/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/edmaputra/ed-iam/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/edmaputra/ed-iam/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/edmaputra/ed-iam/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/edmaputra/ed-iam/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/edmaputra/ed-iam/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/edmaputra/ed-iam/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/edmaputra/ed-iam/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/edmaputra/ed-iam/releases/tag/v0.0.1
