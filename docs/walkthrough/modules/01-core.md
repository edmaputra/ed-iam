# Module 01: ed-iam-core

`ed-iam-core` is the pure domain kernel and application boundary for the entire `ed-iam` system. It contains zero framework dependencies, zero database dependencies, and zero Spring annotations.

---

## 1. Architectural Position

In the Hexagonal Architecture, `ed-iam-core` defines the center of the hexagon:

```
                      ┌─────────────────────────────────┐
                      │    ed-iam-core: Domain Kernel   │
                      │                                 │
                      │   Domain Models & Invariants    │
                      │   Domain Events & Exceptions    │
                      │   Multi-Tenancy Abstractions    │
                      └────────────────┬────────────────┘
                                       │
                      ┌────────────────┴────────────────┐
                      │    Application Ports & Ports    │
                      │                                 │
                      │   Primary Ports: Inbound APIs   │
                      │   Secondary Ports: Outbound SPI │
                      └─────────────────────────────────┘
```

### Constraints Enforced:
* **Zero External Dependencies**: Pure Java 25 only (plus Lombok for boilerplate reduction in specific models).
* **ArchUnit Enforced**: Tested via `DomainArchitectureTest` to guarantee that no Spring, Hibernate, or Jakarta Web packages can ever leak into this module.

---

## 2. Key Mechanisms

### 2.1 Pure Domain Records & Invariants
Domain value objects and models validate their business rules at construction time. If invalid data is supplied, an `IllegalArgumentException` or `NullPointerException` is thrown immediately.

### 2.2 Materialized Path Scope Tree (`ScopeNode`)
Hierarchical scope nodes store their ancestors as a path string:
* Example: `/root-uuid/hospital-uuid/cardiology-uuid/`
* Checking if user has access to a node:
  ```java
  // ScopeSubtreeResolver performs path-prefix matching in O(1) time
  boolean accessible = subtreeResolver.isSubtree(parentPath, targetPath);
  ```

### 2.3 Ports & Adapters Contracts
* **Primary / Inbound Ports**: Interfaces representing use-case entrypoints (e.g. `AuthenticateUserUseCase`, `ManageUserUseCase`).
* **Secondary / Outbound Ports (SPI)**: Contracts required by use cases, implemented by outer infrastructure modules (e.g. `TokenProviderPort`, `UserRepository`).

---

## 3. Key Classes & Responsibilities Reference Table

### 3.1 Domain Models & Value Objects (`domain.model`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `User` | Domain Aggregate | Represents an individual user account, profile details, password hash, status (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`), and superadmin flag. |
| `UserId` | Value Object | Strongly typed identifier for a `User` (backed by UUID). |
| `Role` | Domain Aggregate | Represents a role within a tenant with a set of permission strings and system role flags. |
| `RoleId` | Value Object | Strongly typed identifier for a `Role`. |
| `Group` | Domain Aggregate | Represents a user group within a tenant, linking external IdP groups or internal teams. |
| `GroupId` | Value Object | Strongly typed identifier for a `Group`. |
| `ScopeNode` | Domain Aggregate | Represents a node in an organizational hierarchy, maintaining materialized paths (`/parent/child`) and node types. |
| `ScopeNodeId` | Value Object | Strongly typed identifier for a `ScopeNode`. |
| `UserRoleAssignment` | Association Model | Represents a direct role assignment between a `User`, a `Role`, and an optional `ScopeNodeId` constraint. |
| `GroupRoleAssignment` | Association Model | Represents a role assignment to an entire `Group`, inherited by all group members. |
| `UserGroupMembership` | Association Model | Represents user membership within a `Group`. |
| `UserIdentity` | Domain Aggregate | Links external identity providers (Google, GitHub, Keycloak) to a local `UserId`. |
| `ProviderType` | Enum | Enumeration of identity providers (`LOCAL`, `OIDC`, `SAML`, `API_KEY`). |
| `UserStatus` | Enum | User lifecycle status (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`). |
| `PageQuery` | Value Object | Pagination parameter record (page index, page size, sort order). |
| `PagedResult` | Value Object | Generic paginated response container for domain query results. |

### 3.2 Multi-Tenancy & Context (`domain.tenancy`, `domain.context`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `TenantId` | Value Object | Strongly typed identifier wrapping tenant UUIDv7 values. |
| `TenantOwned` | Domain Interface | Contract implemented by all entities that belong to a specific tenant. |
| `TenantContextBridge` | SPI Hook | Pluggable SPI enabling host applications to propagate tenant IDs into their own context holders. |
| `ActorType` | Enum | Type of principal (`USER`, `SERVICE_ACCOUNT`, `SYSTEM`). |
| `OperationContext` | Context Record | Captures the contextual metadata of an operation (actor, tenant, timestamp). |

### 3.3 Security Abstractions (`domain.security`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `CurrentActor` | Security Value Object | Immutable representation of the calling principal: `userId`, `tenantId`, permissions, and accessible scopes. |
| `CurrentActorProvider` | Security Port | Contract to retrieve the currently authenticated `CurrentActor` from ambient context. |
| `RequirePermission` | Annotation | Declarative annotation for securing endpoints with permissions. |
| `Logical` | Enum | Composition operator (`AND` or `OR`) for multiple permissions in `@RequirePermission`. |

### 3.4 Inbound Ports & Commands (`application.port.in`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `AuthenticateUserUseCase` | Inbound Driving Port | Primary interface for credentials login, token refresh, and tenant switching. |
| `ManageUserUseCase` | Inbound Driving Port | Primary interface for creating users, updating status, and managing direct roles/groups. |
| `ManageRoleUseCase` | Inbound Driving Port | Primary interface for creating, modifying, and listing tenant roles and permissions. |
| `ManageGroupUseCase` | Inbound Driving Port | Primary interface for managing user groups and group-level role assignments. |
| `ManageScopeUseCase` | Inbound Driving Port | Primary interface for tree node creation, hierarchy traversal, and subtree moves. |
| `*Command` Records | Inbound Commands | Immutable input records carrying command parameters: `LoginCommand`, `RefreshTokenCommand`, `SwitchTenantCommand`, `CreateScopeNodeCommand`, `MoveScopeNodeCommand`, etc. |

### 3.5 Outbound Ports / SPIs (`application.port.out`, `domain.repository`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `TokenProviderPort` | Outbound SPI Port | Contract for token generation, claims signing, token validation, and refresh token parsing. |
| `PasswordEncoderPort` | Outbound SPI Port | Contract for hashing and verifying passwords without binding to Spring Security's password encoder directly. |
| `AuthenticationProvider` | Outbound SPI Port | Extensible strategy contract for validating different credential types. |
| `AuthenticationProviderRouter` | Routing Port | Dispatches authentication requests to matching `AuthenticationProvider` implementations. |
| `EventPublisherPort` | Outbound SPI Port | Emits `IamEvent` domain events for audit logging or notification systems. |
| `UserRepository` | Outbound SPI Port | Repository contract for `User` persistence operations. |
| `RoleRepository` | Outbound SPI Port | Repository contract for `Role` persistence operations. |
| `GroupRepository` | Outbound SPI Port | Repository contract for `Group` persistence operations. |
| `ScopeNodeRepository` | Outbound SPI Port | Repository contract for `ScopeNode` persistence and path queries. |
| Association Repositories | Outbound SPI Ports | Repository contracts for relationships: `UserRoleAssignmentRepository`, `GroupRoleAssignmentRepository`, `UserGroupMembershipRepository`, `UserIdentityRepository`. |

### 3.6 Application Services & Models (`application.service`, `application.model`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `ScopeSubtreeResolver` | Pure Domain Service | Calculates path prefixes, checks ancestor/descendant relationships, and validates tree moves. |
| `EffectiveAccess` | DTO / Value Record | Aggregated compilation of a user's combined direct and group roles, permissions, and scope boundaries. |
| `TokenResponse` | DTO Record | Encapsulates issued JWT access token, refresh token, expiry seconds, and token type. |
