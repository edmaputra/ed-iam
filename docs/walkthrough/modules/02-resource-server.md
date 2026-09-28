# Module 02: ed-iam-resource-server

`ed-iam-resource-server` is the lightweight security and token verification module. It enables any Spring Boot service or microservice to validate JWT tokens, enforce permissions, and establish the authenticated `CurrentActor` context **without requiring any database connection or JPA dependency**.

---

## 1. Architectural Position

In downstream microservices, `ed-iam-resource-server` acts as the inbound security adapter layer:

```
 Incoming HTTP Request (Bearer JWT)
                 │
                 ▼
 ┌────────────────────────────────────────────────────────┐
 │ JwtAuthenticationFilter                                │
 │   1. Extracts Bearer token                             │
 │   2. Validates HMAC signature via JwtTokenProvider     │
 │   3. Parses claims (userId, tenantId, permissions)     │
 └───────────────────────┬────────────────────────────────┘
                         │
                         ▼
 ┌────────────────────────────────────────────────────────┐
 │ SecurityContextAccessor (Java 25 ScopedValue)          │
 │   Binds SecurityContextCurrentActor for duration of    │
 │   the request                                          │
 └───────────────────────┬────────────────────────────────┘
                         │
                         ▼
 ┌────────────────────────────────────────────────────────┐
 │ TenantContextBridge (Host Application SPI Hook)        │
 │   Propagates tenantId into host ScopedValue/ThreadLocal│
 └───────────────────────┬────────────────────────────────┘
                         │
                         ▼
 ┌────────────────────────────────────────────────────────┐
 │ Handler Interceptor / SpEL Authorization               │
 │   - RequirePermissionInterceptor (@RequirePermission)  │
 │   - IamSecurityEvaluator (@iam bean in @PreAuthorize)  │
 └────────────────────────────────────────────────────────┘
```

---

## 2. Key Mechanisms

### 2.1 Java 25 `ScopedValue` Security Context
Instead of standard `ThreadLocal` context storage, `SecurityContextAccessor` leverages Java 25's preview/standard `ScopedValue`.
* Context is bound strictly to the HTTP request call stack using `ScopedValue.runWhere(...)`.
* Safe for virtual threads: eliminates memory leaks when virtual threads are parked and resumed across carrier worker threads.

### 2.2 Declarative Endpoint Security (`@RequirePermission`)
Endpoints can be secured without complex SpEL by using `@RequirePermission`:
```java
@RequirePermission(value = {"PATIENT_READ", "CLINIC_ADMIN"}, logical = Logical.OR)
@GetMapping("/{id}")
public PatientDto getPatient(@PathVariable UUID id) { ... }
```
The `RequirePermissionInterceptor` reads method and class-level annotations, checks the current `CurrentActor`, and throws `AccessDeniedException` if authorization fails.

### 2.3 SpEL Security Evaluator (`@iam`)
For services using Spring Security method-level annotations:
```java
@PreAuthorize("@iam.hasPermission('DOC_EDIT') and @iam.canAccessScope(#departmentId)")
public void updateDocument(UUID departmentId, DocumentDto dto) { ... }
```
`IamSecurityEvaluator` provides `@iam` bean methods:
* `hasPermission(String permission)`
* `hasAnyPermission(String... permissions)`
* `hasAllPermissions(String... permissions)`
* `canAccessScope(UUID scopeNodeId)`

---

## 3. Key Classes & Responsibilities Reference Table

| Class / Record | Architectural Role | Objective & Responsibility |
|---|---|---|
| `IamResourceServerAutoConfiguration` | Spring AutoConfiguration | Registers all resource server beans: `JwtTokenProvider`, `SecurityContextAccessor`, `JwtAuthenticationFilter`, `RequirePermissionInterceptor`, and `IamSecurityEvaluator`. |
| `JwtProperties` | Configuration Properties | Maps configuration properties under prefix `iam.jwt.*` (secret key, token expirations, algorithm). |
| `JwtTokenProvider` | Outbound Adapter (JJWT) | Implements `TokenProviderPort`. Signs and parses JWT access and refresh tokens using HMAC-SHA256 (`io.jsonwebtoken 0.12.x`). |
| `JwtAuthenticationFilter` | Inbound Web Filter | Spring `OncePerRequestFilter`. Intercepts incoming HTTP requests, extracts Bearer token, validates signature, scopes the `CurrentActor`, and notifies `TenantContextBridge`. |
| `SecurityContextAccessor` | Security Context Provider | Implements `CurrentActorProvider`. Manages the Java 25 `ScopedValue` context binding the active actor to the current thread/virtual thread. |
| `SecurityContextCurrentActor` | Value Implementation | Concrete package-private record implementing `CurrentActor` populated from decoded JWT claims. |
| `RequirePermissionInterceptor` | MVC Interceptor | Spring MVC `HandlerInterceptor`. Inspects handler method and controller class for `@RequirePermission` and validates actor permissions. |
| `IamSecurityEvaluator` | SpEL Evaluator Bean | Named bean `@iam` for Spring Security SpEL expressions (`@iam.hasPermission(...)`, `@iam.canAccessScope(...)`). |
