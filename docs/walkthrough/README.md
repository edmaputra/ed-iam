# ed-iam Contributor & Newcomer Walkthrough

Welcome to the `ed-iam` contributor walkthrough! This documentation is designed to take you from a high-level conceptual understanding of the platform down into the specific architecture and class-level details of each individual module.

---

## 1. What is `ed-iam`?

`ed-iam` is an enterprise-grade, pluggable Identity & Access Management (IAM) Spring Boot library engineered for **Java 25** and **Spring Boot 4.x**. 

It provides:
* **Multi-Tenant RBAC**: Roles, fine-grained permissions, user groups with role inheritance, and hierarchical organizational scopes.
* **Declarative Security**: Method and controller-level security via `@RequirePermission` and SpEL `@iam` evaluators.
* **Virtual-Thread-Safe Context**: Security context propagation using Java 25's `ScopedValue` API rather than traditional `ThreadLocal` alone.
* **Pluggable Architecture**: Modular packaging allowing downstream microservices to consume only lightweight JWT validation without any database, while auth servers and monoliths can consume the full turnkey management starter with isolated Liquibase migrations.

---

## 2. Walkthrough Roadmap

We recommend reading through the documentation in the following order:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Foundations & Concepts                                   │
│    docs/walkthrough/01-architecture-and-concepts.md         │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Contributor Quickstart & Development Lifecycle           │
│    docs/walkthrough/02-getting-started.md                   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Capabilities Matrix & Product Roadmap                    │
│    docs/walkthrough/03-features-and-roadmap.md              │
└──────────────────────────────┬──────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Multi-Factor Authentication (MFA / TOTP) Guide           │
│    docs/walkthrough/04-mfa-and-totp-guide.md                │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Magic Link & Passwordless Authentication Guide           │
│    docs/walkthrough/05-magic-link-guide.md                  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. Module Deep Dives & Class Architecture Catalogs          │
│    ├── modules/01-core.md            (Domain Kernel & SPIs) │
│    ├── modules/02-resource-server.md (JWT & ScopedValue)    │
│    ├── modules/03-auth.md            (Auth SPI & Providers) │
│    ├── modules/04-management.md      (JPA & Administration) │
│    ├── modules/05-starter.md         (AutoConfiguration)    │
│    └── modules/06-playground-and-testing.md (Playground)    │
└─────────────────────────────────────────────────────────────┘
```

### Module Guides Overview

1. [**Architecture and Core Concepts**](01-architecture-and-concepts.md)  
   The mental model, hexagonal architecture constraints, and key IAM concepts (Tenants, Actors, Roles, Groups, and Hierarchical Scope Trees).

2. [**Getting Started as a Contributor**](02-getting-started.md)  
   Setting up your Java 25 environment, Maven build lifecycle, running tests, code style, and ArchUnit architecture verification.

3. [**Current Features & Product Roadmap**](03-features-and-roadmap.md)  
   Full matrix of operational capabilities (v0.5.0) and the phased roadmap for future enterprise features (SAML 2.0, MFA/TOTP, Magic Link, ABAC, Redis token blacklisting, and OpenTelemetry).

4. [**Multi-Factor Authentication (MFA / TOTP) Guide**](04-mfa-and-totp-guide.md)  
   RFC 6238 TOTP engine, Base32 encoding, two-step login challenges, single-use backup recovery codes, and API lifecycle walkthrough.

5. [**Magic Link & Passwordless Authentication Guide**](05-magic-link-guide.md)  
   Cryptographic URL-safe token generation, single-use atomic consumption, tenant-scoped session resolution, MFA interception, and interactive simulation.

6. [**Module 1: ed-iam-core**](modules/01-core.md)  
   The pure domain kernel. Zero framework dependencies, immutable record invariants, use-case driving ports, and driven SPI repository contracts. Includes a complete class catalog.

7. [**Module 2: ed-iam-resource-server**](modules/02-resource-server.md)  
   Stateless downstream microservice integration. JJWT token parsing, `JwtAuthenticationFilter`, Java 25 `ScopedValue` security context, `@RequirePermission` MVC interceptor, and `@iam` SpEL evaluator. Includes a complete class catalog.

8. [**Module 3: ed-iam-auth**](modules/03-auth.md)  
   Authentication provider router, local BCrypt passwords, federated OIDC identity provisioning, machine-to-machine (M2M) API keys, and single-pass `EffectiveAccessResolver`. Includes a complete class catalog.

9. [**Module 4: ed-iam-management**](modules/04-management.md)  
   Administrative REST endpoints, Spring Data JPA entities, isolated Liquibase migrations (`iam_*`), and hierarchical scope tree management (`ScopeHierarchyService`). Includes a complete class catalog.

10. [**Module 5: ed-iam-starter**](modules/05-starter.md)  
    The turnkey Spring Boot AutoConfiguration module bundling core, resource server, auth, and management for zero-configuration consumers. Includes class catalog.

11. [**Playground & Testing Guide**](modules/06-playground-and-testing.md)  
    The interactive reference application (`samples/ed-iam-playground`), clinical scenario personas, unit and integration testing strategy, and ArchUnit rules.

