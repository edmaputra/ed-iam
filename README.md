<p align="center">
  <img src="docs/assets/ed-iam-icon.svg" width="128" height="128" alt="ed-iam logo" />
</p>

<h1 align="center">ed-iam</h1>

<p align="center">
  <strong>Pluggable, production-ready Multi-Tenant Identity &amp; Access Management (IAM) for Spring Boot 4.x and Java 25.</strong>
</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img src="https://img.shields.io/badge/Java-25-blue.svg" alt="Java 25" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-4.1.1.RELEASE-brightgreen.svg" alt="Spring Boot 4.1.1" /></a>
  <a href="https://central.sonatype.com/artifact/io.github.edmaputra/ed-iam"><img src="https://img.shields.io/maven-central/v/io.github.edmaputra/ed-iam.svg" alt="Maven Central" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" /></a>
</p>

---

`ed-iam` is an enterprise-grade, modular Identity &amp; Access Management Spring Boot Starter designed for multi-tenant architectures. It provides zero-boilerplate tenancy resolution, Role-Based Access Control (RBAC), hierarchical organizational scoping, declarative endpoint permissions, and virtual-thread-ready JWT security context.

For release history and migration details, see the [Changelog](CHANGELOG.md).  
For the architectural overview and product roadmap, see the [Features and Roadmap](docs/features-and-roadmap.md).

---

## Features

- **Multi-Tenant RBAC &amp; Scoping**: Roles, fine-grained permissions, user groups with role inheritance, and path-indexed hierarchical organizational scope trees (`ScopeNode`).
- **Declarative Endpoint Security**:
  - `@RequirePermission` annotation with `Logical.AND` and `Logical.OR` composition for Spring MVC controllers.
  - `@iam` Spring Security / SpEL evaluation bridge (`@iam.hasPermission(...)`, `@iam.canAccessScope(...)`).
- **Turnkey IAM Management REST Endpoints**: Out-of-the-box endpoints for users (`/api/v1/users`), roles (`/api/v1/roles`), groups (`/api/v1/groups`), scopes (`/api/v1/scopes`), and tenant switching (`/api/v1/auth/switch-tenant`).
- **Pluggable Authentication SPI**: Extensible provider router supporting:
  - Local database credentials with BCrypt password hashing.
  - OpenID Connect (OIDC) / OAuth2 federated identities with auto-provisioning.
  - Machine-to-Machine (M2M) API keys.
- **JWT Engine &amp; Virtual Thread Context**:
  - High-throughput HMAC-SHA256 token generation, signing, and verification.
  - Non-blocking `JwtAuthenticationFilter` with Java 25 `ScopedValue`-backed `CurrentActorProvider`.
  - Pluggable `TenantContextBridge` SPI for seamless host-application multi-tenant propagation.
- **Isolated Schema Migrations**: Module-scoped Liquibase migrations managing namespaced `iam_*` tables with zero host-schema collisions.
- **Modular Packaging**: Choose the full turnkey starter or a lightweight zero-DB resource server for downstream microservices.

---

## Modular Architecture

`ed-iam` is organized into focused, decoupled modules:

| Module | Description | Recommended For |
|---|---|---|
| `ed-iam-starter` | Turnkey aggregator bundling core, resource server, management, persistence, and Liquibase. | Identity &amp; authentication servers, monoliths |
| `ed-iam-resource-server` | Lightweight library with JWT parsing, `@RequirePermission`, SpEL `@iam` evaluator, and `ScopedValue` context. Zero JPA or Liquibase dependency. | Downstream microservices, API gateways |
| `ed-iam-management` | Administrative services, JPA entity repositories, REST controllers, and Liquibase auto-configuration. | Custom IAM administration services |
| `ed-iam-core` | Pure domain models (`User`, `Role`, `Group`, `ScopeNode`), domain events, and ports with zero framework coupling. | Domain model extensions |

---

## Installation

### Option 1: Full Turnkey Starter (Recommended for IAM Services)

Add `ed-iam-starter` to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.edmaputra</groupId>
    <artifactId>ed-iam-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Option 2: Lightweight Resource Server (For Downstream Microservices)

If your microservice only needs to validate JWTs, enforce `@RequirePermission`, and access `CurrentActor` without database tables:

```xml
<dependency>
    <groupId>io.github.edmaputra</groupId>
    <artifactId>ed-iam-resource-server</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Configuration

Configure the starter in `application.yml` or `application.properties`:

```yaml
iam:
  jwt:
    secret: "your-at-least-256-bit-secret-key-here-that-is-very-secure!"
    access-token-expiration-seconds: 3600    # 1 hour (default)
    refresh-token-expiration-seconds: 604800 # 7 days (default)
  security:
    permissions:
      enabled: true                          # Enable @RequirePermission interceptor & @iam evaluator (default: true)
  management:
    endpoints:
      enabled: true                          # Enable built-in IAM management REST controllers (default: true)

spring:
  liquibase:
    enabled: true                            # Enable IAM Liquibase schema migrations (default: true)
```

---

## Usage

### 1. Declarative Endpoint Security (`@RequirePermission`)

Protect Spring MVC endpoints declaratively with `@RequirePermission`:

```java
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    // Single permission check
    @RequirePermission("DOC_READ")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDto> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id));
    }

    // Composite permissions with Logical.AND or Logical.OR
    @RequirePermission(value = {"DOC_WRITE", "DOC_ADMIN"}, logical = Logical.OR)
    @PostMapping
    public ResponseEntity<DocumentDto> createDocument(@RequestBody DocumentRequest request) {
        return ResponseEntity.ok(documentService.create(request));
    }
}
```

### 2. SpEL Expression Security (`@iam`)

Use the `@iam` security evaluator bean within Spring Security's `@PreAuthorize`:

```java
@Service
public class ClinicalRecordService {

    @PreAuthorize("@iam.hasPermission('RECORD_VIEW') and @iam.canAccessScope(#departmentId)")
    public ClinicalRecord getRecord(UUID departmentId, UUID recordId) {
        return recordRepository.findByDepartmentAndId(departmentId, recordId);
    }
}
```

### 3. Accessing Authenticated User Context

Inject `CurrentActorProvider` anywhere in your Spring services or controllers:

```java
@Service
public class DocumentService {

    private final CurrentActorProvider currentActorProvider;

    public DocumentService(CurrentActorProvider currentActorProvider) {
        this.currentActorProvider = currentActorProvider;
    }

    public void publishDocument(UUID scopeNodeId) {
        CurrentActor actor = currentActorProvider.requireCurrentActor();

        // Check permission programmatically
        if (!actor.hasPermission("DOC_PUBLISH")) {
            throw new AccessDeniedException("Missing required permission.");
        }

        // Check organizational scope boundary
        if (!actor.canAccessScope(scopeNodeId)) {
            throw new AccessDeniedException("Out of scope boundary.");
        }

        // Access actor details
        UUID userId = actor.userId();
        String email = actor.email();
        UUID tenantId = actor.tenantId();
    }
}
```

### 4. Multi-Tenant Context Propagation (`TenantContextBridge`)

If your host application manages tenancy via `ScopedValue` or `ThreadLocal`, implement `TenantContextBridge`:

```java
@Component
public class AppTenantContextBridge implements TenantContextBridge {

    @Override
    public <E extends Throwable> void runWithTenant(UUID tenantId, ThrowingRunnable<E> runnable) throws E {
        TenantContextHolder.runWith(tenantId, runnable::run);
    }
}
```

---

## Interactive Playground &amp; Reference Application

Try `ed-iam` in action with our pre-built reference application and IAM management console:

```bash
docker run -d --name ed-iam-playground -p 8080:8080 ghcr.io/edmaputra/ed-iam-playground:latest
```

Open [http://localhost:8080](http://localhost:8080) to explore pre-seeded personas, custom credential login, token inspection, multi-tenant context switching, hierarchical scope trees, and user lifecycle management. For complete instructions and Docker Compose setup, see [samples/ed-iam-playground](samples/ed-iam-playground/README.md).

---

## Building &amp; Running Tests

Requirements:
- Java 25
- Maven 3.9+

```bash
./mvnw clean install
```
