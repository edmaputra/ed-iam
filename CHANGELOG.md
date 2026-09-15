# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/edmaputra/ed-iam/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/edmaputra/ed-iam/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/edmaputra/ed-iam/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/edmaputra/ed-iam/releases/tag/v0.0.1
