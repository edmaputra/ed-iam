# Module 06: Playground & Testing Architecture

This guide covers the reference application (`samples/ed-iam-playground`), its multi-tenant clinical scenarios, and the comprehensive testing strategy used across `ed-iam` (including ArchUnit architecture rules).

---

## 1. The Interactive Playground (`samples/ed-iam-playground`)

`samples/ed-iam-playground` is an end-to-end reference application demonstrating how a real-world multi-tenant system integrates `ed-iam`.

### 1.1 Clinical Domain Scenario
The playground models two independent hospital tenants:
1. **St. Jude Hospital**:
   * Hierarchy: `Hospital -> West Campus -> Cardiology Ward`
   * Personas: Hospital Admin, Senior Cardiologist, Floor Nurse
2. **Metro General Hospital**:
   * Hierarchy: `Hospital -> Emergency Department`
   * Personas: Hospital Admin, ER Doctor

### 1.2 Host Application Tenant Bridging (`TenantContextBridge`)
The playground shows how the host application synchronizes `ed-iam` tenancy with its own domain:
* `HostTenantContext`: The host application's tenant holder.
* `HostTenantContextBridge`: Implements `ed-iam`'s `TenantContextBridge` SPI hook. Whenever `JwtAuthenticationFilter` validates a token, the bridge automatically updates `HostTenantContext`.

---

## 2. Key Playground Classes & Responsibilities

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `PlaygroundApplication` | Spring Boot Main | Entrypoint for the playground web application and console dashboard. |
| `PlaygroundDataSeeder` | Data Seeder | Seeds tenants, users, password hashes, roles, permissions, hierarchical scope nodes, and clinical records. |
| `HostTenantContext` | Host Context | Simulates the consuming application's custom tenant holder. |
| `HostTenantContextBridge` | SPI Implementation | Implements `TenantContextBridge` to bridge `ed-iam` tenancy changes into `HostTenantContext`. |
| `PatientRecordController` | Sample REST API | Protected clinical endpoints demonstrating `@RequirePermission` and `@iam.canAccessScope(#scopeId)`. |
| `CurrentActorContextController` | Debug Controller | Exposes `/api/v1/playground/actor` for live inspection of the current authenticated `CurrentActor` claims. |
| `PlaygroundViewController` | UI Controller | Serves the interactive browser frontend at `http://localhost:8080`. |
| `PatientRecord` | Host Domain Entity | Host-level clinical entity constrained by `tenantId` and departmental `scopeNodeId`. |

---

## 3. Testing Architecture

`ed-iam` uses a multi-layered testing pyramid:

```
                   ▲
                  / \
                 /   \
                / E2E \       Playground Application Tests
               /-------\      (Context Boot & Flow Verification)
              /  Integ  \     Controller Slices & JPA Repository Tests
             /-----------\    (Liquibase Migrations, REST Mappings)
            / Arch & Unit \   ArchUnit Hexagonal Rules, Model Invariants,
           /---------------\  JwtTokenProvider, ScopeSubtreeResolver
```

### 3.1 ArchUnit Architecture Tests (`DomainArchitectureTest`)
Located in `ed-iam-core/src/test/java/io/github/edmaputra/iam/domain/DomainArchitectureTest.java`:
* Asserts that `domain` packages have **no dependencies** on Spring, Spring Boot, Hibernate, or Jakarta Web packages.
* Verifies that dependencies flow strictly inwards: `adapter -> application -> domain`.
* Verifies that domain DTOs, value objects, and commands are immutable Java records.

```bash
# Run architecture tests specifically:
./mvnw test -Dtest=DomainArchitectureTest
```

### 3.2 Unit & Invariant Testing
Every domain entity and value object constructor is thoroughly tested against invariant violations:
* Non-null constraints, blank string validation, and valid UUIDv7 formats.
* Examples: `UserTest`, `RoleTest`, `ScopeNodeTest`, `GroupTest`, `CommandsAndInvariantsTest`.

### 3.3 Integration & Slice Testing
* **Token Verification**: `JwtTokenProviderTest` validates HMAC signatures, expiry times, and tamper detection.
* **Effective Access**: `EffectiveAccessResolverTest` verifies that direct and group-inherited roles are combined correctly.
* **REST & Web Slices**: `AuthControllerTest`, `UserControllerTest` use `MockMvc` to verify HTTP status codes and RFC 7807 error responses.

---

## 4. Verification Command Summary

```bash
# Run all unit tests
./mvnw test

# Run tests for a specific module
./mvnw test -pl ed-iam-core
./mvnw test -pl ed-iam-resource-server
./mvnw test -pl ed-iam-auth
./mvnw test -pl ed-iam-management

# Full verify with JaCoCo coverage reports
./mvnw clean verify
```
