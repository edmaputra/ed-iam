# Agent Guide: ed-iam

Central multi-tenant Identity & Access Management Spring Boot starter library.

---

## 1. Stack & Versions
- **Language**: Java 25
- **Framework**: Spring Boot 4.1.1
- **Build Tool**: Maven (`./mvnw`)
- **JWT Engine**: JJWT 0.12.6
- **Architecture Testing**: ArchUnit 1.4.0
- **Standards**: Inherited from `.agents/rules/java-kotlin/` and `.agents/rules/shared/`

---

## 2. Multi-Module Layout & Hexagonal Architecture

Dependencies flow strictly inward: `adapter` -> `application` -> `domain`.

- **`ed-iam-core`**: Pure domain kernel & application interfaces (zero framework dependencies).
  - `domain/model/`: Pure Java records (Entities & Value Objects) with compact constructors enforcing business invariants.
  - `domain/repository/`: Outbound repository contracts (SPIs).
  - `domain/event/`: Immutable domain events (`IamEvent`, `IamEventTypes`).
  - `domain/context/`: Context models (`OperationContext`, `ActorType`).
  - `domain/tenancy/`: Multi-tenancy abstractions (`TenantId`, `TenantOwned`, `TenantContextBridge`).
  - `domain/security/`: Security abstractions (`CurrentActor`, `CurrentActorProvider`, `@RequirePermission`).
  - `application/port/in/`: Primary/driving use-case interfaces and command records.
  - `application/port/out/`: Outbound SPI ports (`TokenProviderPort`, `EventPublisherPort`, etc.).
  - `application/service/`: Pure orchestration services (`ScopeSubtreeResolver`).
- **`ed-iam-resource-server`**: Spring Security resource server integration.
  - `adapter/security/`: Stateless JWT validation filter, `ScopedValue` context accessor, SpEL evaluator (`@iam`), `@RequirePermission` handler interceptor.
- **`ed-iam-auth`**: Authentication and token issuance module.
  - `adapter/rest/`: `AuthController` (`/api/v1/auth/*`) and auth request DTOs (`LoginRequest`, `RefreshTokenRequest`, `SwitchTenantRequest`).
  - `adapter/security/`: `BCryptPasswordEncoderAdapter`, authentication providers (`LocalPasswordAuthProvider`, `ApiKeyAuthProvider`, `OidcAuthProvider`).
  - `application/service/`: `AuthenticationService`, `EffectiveAccessResolver`, `FederatedIdentityService`.
- **`ed-iam-management`**: Management domain implementations & web adapters.
  - `application/service/`: Use case implementations (`UserManagementService`, `RoleManagementService`, etc.).
  - `adapter/rest/`: `@RestController` classes and request/response DTOs under `/api/v1/` (`UserController`, `RoleController`, `GroupController`, `ScopeController`).
  - `adapter/persistence/`: Spring Data JPA entities, repositories, and persistence adapters.
- **`ed-iam-starter`**: Aggregated Spring Boot AutoConfiguration for zero-configuration consumers.
- **`samples/ed-iam-playground`**: Interactive sample application demonstrating multi-tenant clinical scenarios.

---

## 3. Key Commands

- **Build**: `./mvnw clean package`
- **Unit & Architecture Tests**: `./mvnw test`
- **Full Verification & JaCoCo Coverage**: `./mvnw clean verify`
- **Refresh Agent Structure Map**: `python3 .agents/scripts/scan-structure.py --force`

---

## 4. Agent Guidelines & Constraints 🔴 MUST

1. **Domain Purity**: NEVER import Spring, JPA, Hibernate, or Web dependencies inside `ed-iam-core`'s `domain` package.
2. **Inward Dependencies**: Enforce hexagonal boundaries; controllers must never call repositories directly.
3. **Immutability**: Use Java records for all DTOs, domain models, value objects, and commands.
4. **Structured Discovery**: Reference `.agents/project-structure.json` for immediate directory mapping.
5. **Javadoc Standards**: All top-level types MUST include `@author edmaputra` and appropriate `@since` tags.
