# Module 04: ed-iam-management

`ed-iam-management` provides the persistence implementations, administrative domain services, isolated database schema migrations, and turnkey REST management APIs for users, roles, groups, and organizational scope hierarchies.

---

## 1. Architectural Position

`ed-iam-management` houses both inbound driving REST adapters and outbound driven persistence adapters:

```
 HTTP /api/v1/{users, roles, groups, scopes}
                     │
                     ▼
 ┌────────────────────────────────────────────────────────┐
 │ Inbound REST Adapters (@RestController)                │
 │   UserController, RoleController, GroupController,     │
 │   ScopeController, IamExceptionHandler                 │
 └───────────────────┬────────────────────────────────────┘
                     │ (Commands & Queries)
                     ▼
 ┌────────────────────────────────────────────────────────┐
 │ Application Services (Use Case Implementations)        │
 │   UserManagementService, RoleManagementService,        │
 │   GroupManagementService, ScopeHierarchyService        │
 └───────────────────┬────────────────────────────────────┘
                     │ (Domain Repositories SPI)
                     ▼
 ┌────────────────────────────────────────────────────────┐
 │ Outbound Persistence Adapters                          │
 │   UserRepositoryAdapter, RoleRepositoryAdapter, etc.   │
 └───────────────────┬────────────────────────────────────┘
                     │ (JPA Operations)
                     ▼
 ┌────────────────────────────────────────────────────────┐
 │ Spring Data JPA Repositories & Entities                │
 │   UserJpaRepository, UserJpaEntity (iam_user), etc.    │
 └───────────────────┬────────────────────────────────────┘
                     │
                     ▼
 ┌────────────────────────────────────────────────────────┐
 │ Liquibase Migrations (Isolated iam_* tables)           │
 │   IamLiquibaseAutoConfiguration (db.changelog-iam.json)│
 └────────────────────────────────────────────────────────┘
```

---

## 2. Key Mechanisms

### 2.1 Isolated Schema Migrations (`iam_*`)
`IamLiquibaseAutoConfiguration` runs module-specific changelogs (`db.changelog-iam.json`) against the database.
* All tables are prefixed with `iam_` (`iam_user`, `iam_role`, `iam_group`, `iam_scope_node`, `iam_user_role_assignment`, etc.).
* Zero foreign-key coupling to host-application tables, guaranteeing collision-free operation in shared databases.

### 2.2 Hierarchical Tree Operations (`ScopeHierarchyService`)
Manages tree manipulations for organizational units:
* Node creation automatically computes the materialized path: `/rootId/parentId/newId/`.
* Moving subtrees (`moveScopeNode`) recalculates all descendant materialized paths in a single transactional batch.
* Prevents cyclical parenting and invalid tree states.

### 2.3 Management Endpoints Toggle
Host applications can disable the built-in REST controllers while retaining the underlying service and repository beans by setting:
```yaml
iam:
  management:
    endpoints:
      enabled: false  # Disables @RestController beans, keeps services active
```

---

## 3. Key Classes & Responsibilities Reference Table

### 3.1 REST Controllers & Exception Advice (`adapter.rest`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `UserController` | Driving REST Controller | Exposes `/api/v1/users` for user creation, status changes, role assignments, and group memberships. |
| `RoleController` | Driving REST Controller | Exposes `/api/v1/roles` for creating custom tenant roles, updating permissions, and listing roles. |
| `GroupController` | Driving REST Controller | Exposes `/api/v1/groups` for managing groups, group members, and group-level role assignments. |
| `ScopeController` | Driving REST Controller | Exposes `/api/v1/scopes` for tree hierarchies, creating child nodes, moving subtrees, and querying trees. |
| `IamExceptionHandler` | REST Exception Advice | `@RestControllerAdvice` mapping domain exceptions (`UserNotFoundException`, `AccessDeniedException`, etc.) to standard HTTP responses (RFC 7807 Problem Details). |
| `*ManagementDtos` | REST DTO Records | Request and response payload records: `UserManagementDtos`, `RoleManagementDtos`, `GroupManagementDtos`, `ScopeManagementDtos`. |

### 3.2 Application Services (`application.service`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `UserManagementService` | Application Service | Implements `ManageUserUseCase`. Orchestrates user lifecycle, password hashing, and user-role-group bindings. |
| `RoleManagementService` | Application Service | Implements `ManageRoleUseCase`. Manages role CRUD and prevents unauthorized modification of system-protected roles. |
| `GroupManagementService` | Application Service | Implements `ManageGroupUseCase`. Handles group CRUD, user membership additions/removals, and group roles. |
| `ScopeHierarchyService` | Application Service | Implements `ManageScopeUseCase`. Manages tree hierarchies, path generation, cyclical move prevention, and tree assembly. |

### 3.3 Persistence Entities & Adapters (`adapter.persistence`)

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `UserJpaEntity` | JPA Entity | Maps to table `iam_user`. |
| `RoleJpaEntity` | JPA Entity | Maps to table `iam_role`. |
| `GroupJpaEntity` | JPA Entity | Maps to table `iam_group`. |
| `ScopeNodeJpaEntity` | JPA Entity | Maps to table `iam_scope_node`. |
| `*AssignmentJpaEntity` | JPA Entities | Relationship mappings: `UserRoleAssignmentJpaEntity`, `GroupRoleAssignmentJpaEntity`, `UserGroupMembershipJpaEntity`, `UserIdentityJpaEntity`. |
| `*JpaRepository` | Spring Data Repositories | Spring Data interfaces extending `JpaRepository` and `JpaSpecificationExecutor`. |
| `UserSpecifications` | Query Specification | Dynamic JPA Criteria specifications for filtering users by status, email, or creation date. |
| `*RepositoryAdapter` | Driven Persistence Adapters | Implements core repository SPIs (`UserRepositoryAdapter`, `RoleRepositoryAdapter`, etc.), translating between JPA entities and pure domain models. |

### 3.4 AutoConfiguration Classes

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `IamManagementAutoConfiguration` | Spring AutoConfiguration | Registers management services and REST controllers conditionally based on `iam.management.endpoints.enabled`. |
| `IamScopeAutoConfiguration` | Spring AutoConfiguration | Configures `ScopeHierarchyService` and `ScopeController`. |
| `IamLiquibaseAutoConfiguration` | Spring AutoConfiguration | Executes isolated Liquibase migrations for `iam_*` tables. |
| `IamSecurityAutoConfiguration` | Spring AutoConfiguration | Configures management security rules. |
