# IAM Spring Boot Starter

[![Java](https://img.shields.io/badge/Java-25-blue.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1.RELEASE-brightgreen.svg)](https://spring.io/projects/spring-boot)

A pluggable, production-ready **Identity & Access Management (IAM)** Spring Boot Starter designed for multi-tenant, enterprise applications.

---

## Features

- **Multi-Tenant RBAC**: Roles, fine-grained permissions, user groups, and hierarchical organizational scope trees (`ScopeNode`).
- **Spring Boot 4.x Auto-Configuration**: Automatic discovery of beans, JPA entities, and repository adapters without manual `@ComponentScan`.
- **Pluggable Authentication SPI**: Router with built-in support for:
  - Local database credentials (with BCrypt password hashing).
  - OpenID Connect (OIDC) / OAuth2 federated identities.
  - Extensible `AuthenticationProvider` SPI for custom schemes (SAML, LDAP, API keys).
- **JWT Engine & Security Context**:
  - HMAC-SHA256 access and refresh token generation and verification.
  - Non-blocking `JwtAuthenticationFilter` with Java 25 `ScopedValue`-backed `CurrentActorProvider`.
  - Pluggable `TenantContextBridge` SPI for seamless host-application multi-tenant context propagation.
- **Isolated Schema Migrations**: Module-scoped Liquibase migrations managing `iam_*` tables.

For our complete capability breakdown and future plans, see the [Features and Roadmap](docs/features-and-roadmap.md).

---

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.edmaputra</groupId>
    <artifactId>iam-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Configuration

Configure the starter in `application.yml` or `application.properties`:

```yaml
iam:
  jwt:
    secret: "your-at-least-256-bit-secret-key-here-that-is-very-secure!"
    access-token-expiration-seconds: 3600    # 1 hour
    refresh-token-expiration-seconds: 604800 # 7 days
```

---

## Usage

### 1. Accessing Authenticated User Context

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

        // Check permission
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

### 2. Multi-Tenant Context Propagation (Optional)

If your host application uses a multi-tenancy manager (like `ScopedValue` or `ThreadLocal`), implement `TenantContextBridge`:

```java
@Component
public class AppTenantContextBridge implements TenantContextBridge {

    @Override
    public <E extends Throwable> void runWithTenant(UUID tenantId, ThrowingRunnable<E> runnable) throws E {
        // Bind tenant ID to your context and execute
        TenantContextHolder.runWith(tenantId, runnable::run);
    }
}
```

---

## Building & Running Tests

Requirements:
- Java 25
- Maven 3.9+

```bash
./mvnw clean install
```
