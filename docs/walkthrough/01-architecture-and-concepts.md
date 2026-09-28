# 01. Architecture and Core Concepts

This document establishes the architectural principles, mental model, and foundational concepts that govern the entire `ed-iam` codebase.

---

## 1. Architectural Style: Hexagonal Architecture (Ports & Adapters)

`ed-iam` strictly follows **Hexagonal Architecture**. Dependencies flow inwards towards the domain kernel:

```
               ┌────────────────────────────────────────────────────────┐
               │              Primary / Driving Adapters                │
               │   REST Controllers (@RestController, Spring MVC)       │
               │   Security Filters (JwtAuthenticationFilter)           │
               └───────────────────────────┬────────────────────────────┘
                                           │ (calls)
                                           ▼
               ┌────────────────────────────────────────────────────────┐
               │             Inbound Ports / Use Cases (API)            │
               │   AuthenticateUserUseCase, ManageUserUseCase, ...       │
               └───────────────────────────┬────────────────────────────┘
                                           │ (orchestrates)
                                           ▼
               ┌────────────────────────────────────────────────────────┐
               │                     Domain Kernel                      │
               │   Aggregates & Entities: User, Role, Group, ScopeNode  │
               │   Value Objects: TenantId, UserId, RoleId              │
               │   Invariants & Domain Events: IamEvent                 │
               └───────────────────────────▲────────────────────────────┘
                                           │ (implemented by)
                                           │
               ┌───────────────────────────┴────────────────────────────┐
               │             Outbound Ports / Driven SPIs               │
               │   UserRepository, RoleRepository, TokenProviderPort,   │
               │   PasswordEncoderPort, EventPublisherPort              │
               └───────────────────────────▲────────────────────────────┘
                                           │ (implemented by)
               ┌───────────────────────────┴────────────────────────────┐
               │             Secondary / Driven Adapters                │
               │   Spring Data JPA Adapters, BCryptPasswordEncoder      │
               │   JJWT Token Provider, Liquibase Schema Migrations     │
               └────────────────────────────────────────────────────────┘
```

### The Golden Rule: Domain Purity
* The `domain` package in `ed-iam-core` contains **pure Java**.
* **NEVER** import Spring, Spring Boot, Jakarta Persistence (JPA), Hibernate, or Web packages inside `ed-iam-core`.
* Invariants are enforced directly inside record and class constructors.
* This rule is automatically verified during test execution by **ArchUnit** (`DomainArchitectureTest`).

---

## 2. Java 25 & Modern Platform Features

`ed-iam` is built for modern Java runtimes:

### 2.1 Java 25 `ScopedValue` Context
Traditional Spring Security architectures store the authenticated principal in a `ThreadLocal` (`SecurityContextHolder`). In high-concurrency environments running on Java Virtual Threads, `ThreadLocal` introduces unbounded memory overhead and inheritance complexities.

`ed-iam` uses Java 25's `ScopedValue` API in `SecurityContextAccessor`:
* Context is bound strictly to the execution scope of the HTTP request.
* Bound data is completely immutable once scoped.
* Zero memory leakage across virtual thread pooling.

### 2.2 Domain Records & Compact Constructors
Domain value objects, commands, and DTOs are declared as Java `record` types. Invariants (such as non-null checks, non-blank strings, and valid UUIDs) are validated in compact constructors:

```java
public record TenantId(UUID value) {
    public TenantId {
        Objects.requireNonNull(value, "Tenant ID value must not be null");
    }
}
```

### 2.3 RFC 9562 UUIDv7
Identity identifiers across `ed-iam` (`UserId`, `RoleId`, `TenantId`, `ScopeNodeId`) support time-ordered RFC 9562 UUIDv7 via the internal `UuidV7` generator. UUIDv7 guarantees chronological sequential ordering, which prevents database index fragmentation in high-write B-Tree indexes.

---

## 3. Core Domain Concepts & Mental Model

```
 ┌──────────────┐
 │   Tenant     │
 └──────┬───────┘
        │ 1..*
        ▼
 ┌──────────────┐       assigned to       ┌──────────────┐
 │     User     │◄───────────────────────►│  User Group  │
 └──────┬───────┘                         └──────┬───────┘
        │                                        │
        │ direct role                            │ group inherited role
        ▼                                        ▼
   ┌──────────────────────────────────────────────────┐
   │                       Role                       │
   │               (Collection of Permissions)        │
   └──────────────────────────┬───────────────────────┘
                              │
                              ▼
   ┌──────────────────────────────────────────────────┐
   │            Hierarchical Scope Boundary           │
   │  Path: /Hospital-A/Clinic-West/Cardiology/Ward-1 │
   └──────────────────────────────────────────────────┘
```

### 3.1 Multi-Tenancy (`TenantId`, `TenantOwned`)
* Every resource in the system belongs to a `TenantId`.
* **Zero Host Schema Collisions**: All IAM tables use the `iam_*` prefix (`iam_user`, `iam_role`, `iam_group`, `iam_scope_node`).
* **Tenant Auto-Resolution**: Users log in with standard email and password; single-tenant users are automatically bound to their tenant. Multi-tenant users receive their list of available tenant IDs and can switch tenants post-login (`POST /api/v1/auth/switch-tenant`).
* **Host Application Integration (`TenantContextBridge`)**: A pluggable SPI allowing host applications to propagate tenancy context into their own `ThreadLocal` or `ScopedValue` systems seamlessly.

### 3.2 Principal / Actor Model (`CurrentActor`, `ActorType`)
* When a request is authenticated, the principal is mapped into a `CurrentActor` record.
* `CurrentActor` exposes:
  * `userId()` & `email()`
  * `tenantId()`
  * `actorType()` (`USER`, `SERVICE_ACCOUNT`, `SYSTEM`)
  * `roles()` & `permissions()`
  * `accessibleScopeNodeIds()`

### 3.3 Permissions, Roles, and Groups
* **Permissions**: Fine-grained string capabilities (e.g. `USER_READ`, `RECORD_WRITE`, `PATIENT_DISCHARGE`).
* **Roles**: Named collections of permissions (e.g. `CLINICIAN`, `TENANT_ADMIN`).
* **Groups**: Organizational collections of users. Groups are assigned roles; any user belonging to a group automatically inherits all roles assigned to that group.

### 3.4 Hierarchical Scope Trees (`ScopeNode`)
Access in enterprise organizations (such as healthcare or education) is often bounded by organizational hierarchy:
* Organizations are structured as trees: `Hospital -> Clinic -> Department -> Ward`.
* `ScopeNode` uses **Materialized Path Indexing** (e.g., `/root-uuid/hospital-uuid/ward-uuid/`).
* Path-indexing enables immediate $O(1)$ ancestor and descendant authorization checks:
  ```java
  // Check if the current actor has access to a specific scope node or its parent tree
  actor.canAccessScope(wardScopeNodeId);
  ```

### 3.5 Effective Access Resolution (`EffectiveAccessResolver`)
Access calculations do not happen piecemeal across multiple database queries during request handling. Instead, during authentication or token refresh, `EffectiveAccessResolver` performs a single-pass resolution that consolidates:
1. Direct roles assigned to the user.
2. Group roles inherited through group memberships.
3. Wildcard or combined permissions.
4. Accessible organizational scope nodes.

The result is bundled into an immutable `EffectiveAccess` snapshot and encoded directly into the JWT claims.
