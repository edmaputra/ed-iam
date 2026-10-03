# Module 05: ed-iam-starter

`ed-iam-starter` is the turnkey aggregator module for `ed-iam`. It packages the entire IAM stack—core domain, security filter, authentication engine, management services, JPA persistence, and Liquibase migrations—into a single dependency for zero-configuration consumers.

---

## 1. Architectural Position

In consuming applications (e.g. Identity Services or Monolithic applications), `ed-iam-starter` acts as the single entrypoint dependency:

```
 Consuming Spring Boot Application (pom.xml)
                 │
                 ▼
 ┌────────────────────────────────────────────────────────┐
 │ ed-iam-starter                                         │
 └───────┬──────────────┬───────────────┬─────────────────┘
         │              │               │
         ▼              ▼               ▼
 ┌───────────────┐ ┌─────────────┐ ┌──────────────────────┐
 │ ed-iam-core   │ │ ed-iam-auth │ │ ed-iam-management    │
 └───────┬───────┘ └──────┬──────┘ └──────────┬───────────┘
         │                │                   │
         └────────► ┌─────▼───────────────────▼─────────┐
                    │ ed-iam-resource-server            │
                    └───────────────────────────────────┘
```

---

## 2. Key Mechanisms

### 2.1 Auto-Configuration Chain & Order
When a Spring Boot application starts with `ed-iam-starter` on the classpath, the auto-configuration classes activate in strict sequence:
1. **`IamResourceServerAutoConfiguration`**: Configures JJWT provider, `SecurityContextAccessor`, and `JwtAuthenticationFilter`.
2. **`IamAuthAutoConfiguration`** (after resource server): Configures password encoders, providers, `EffectiveAccessResolver`, `AuthenticationService`, and `/api/v1/auth/*` endpoints.
3. **`IamManagementAutoConfiguration`**: Configures domain management services (`UserManagementService`, `RoleManagementService`, etc.) and management REST endpoints (`/api/v1/users`, `/roles`, etc.).
4. **`IamLiquibaseAutoConfiguration`**: Executes `db.changelog-iam.json` to ensure all `iam_*` tables exist before repositories initialize.

### 2.2 Turnkey Defaults with Customization Overrides
All beans in `ed-iam` are registered with `@ConditionalOnMissingBean`. If a consuming application declares its own `TokenProviderPort`, `PasswordEncoderPort`, or `TenantContextBridge`, the custom bean takes precedence automatically.

---

## 3. Key Classes & Responsibilities Reference Table

| Class / Component | Architectural Role | Objective & Responsibility |
|---|---|---|
| `IamStarter` | Marker Class | Top-level anchor and marker class representing the turnkey starter module. |
| `package-info.java` | Package Descriptor | Module-level Javadoc metadata defining the starter capabilities and `@author` attribution. |
| `pom.xml` | Maven Aggregator POM | Bundles transitive dependencies: `ed-iam-core`, `ed-iam-resource-server`, `ed-iam-auth`, `ed-iam-management`, Spring Boot Starter Web, JPA, Security, and Liquibase. |
