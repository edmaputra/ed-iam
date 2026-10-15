# 07. Auto-Configuration & Coexistence Guide

When embedding `ed-iam` into an existing Spring Boot application that already has its own Spring Security filter chains, Liquibase migrations, or custom error handling, auto-configuration collisions can occur if the library's defaults conflict with the host application's architecture.

This guide explains how to configure `ed-iam` for clean, zero-exclusion coexistence without ever needing:
```properties
spring.autoconfigure.exclude=...IamResourceServerAutoConfiguration,...IamLiquibaseAutoConfiguration
```

---

## 1. Why Do Collisions Occur?

### Problem 1: Spring Boot Servlet Filter Registration
In Spring Boot, any bean implementing `jakarta.servlet.Filter` (such as `JwtAuthenticationFilter`) is **automatically registered with the servlet container** across `/*` unless explicitly prevented.
* **Symptom**: `JwtAuthenticationFilter` executes *before* or *outside* your custom Spring Security `SecurityFilterChain`. If a request arrives with no Bearer token or an external token format, `JwtAuthenticationFilter` may abort the request with HTTP 401 before your application's own security filters can evaluate it.
* **Solution**: Set `ed-iam.security.filter.register-servlet=false`. This keeps the `JwtAuthenticationFilter` bean available in the application context so you can place it precisely inside your `SecurityFilterChain`, but stops Spring Boot from auto-registering it to the root servlet container.

### Problem 2: Liquibase Schema Migration Collisions
When host applications already manage database migrations via Liquibase, Spring Boot's standard `spring.liquibase.enabled` flag would turn off *both* the host's migrations and IAM's migrations.
* **Symptom**: You could not disable IAM table creation (`iam_*`) without also disabling your application's own Liquibase migrations, forcing you to exclude `IamLiquibaseAutoConfiguration`.
* **Solution**: `ed-iam` provides a dedicated `ed-iam.liquibase.enabled` toggle and `@ConditionalOnMissingBean(name = "iamLiquibase")`.

### Problem 3: Multi-Provider Authentication Fallthrough
If your application accepts multiple token types (e.g., an IAM token on some paths, an external OAuth2/OIDC token or API key on others), an unrecognized or invalid IAM token should not immediately abort the request.
* **Solution**: Set `ed-iam.security.filter.failure-passthrough=true`. The filter will cleanly delegate to the rest of the chain (`filterChain.doFilter(request, response)`) when token validation fails.

---

## 2. Configuration Properties Reference

All auto-configurations are namespaced under the dedicated `ed-iam.*` prefix to avoid property naming collisions with consuming host applications.

### Resource Server & Security Filter

| Property | Default | Description |
|---|---|---|
| `ed-iam.resource-server.enabled` | `true` | Master switch for `IamResourceServerAutoConfiguration`. If `false`, disables all JWT parsing, `JwtAuthenticationFilter`, and security evaluators. |
| `ed-iam.security.filter.enabled` | `true` | Enables or disables the `JwtAuthenticationFilter` bean. |
| `ed-iam.security.filter.register-servlet` | `true` | When `false`, suppresses automatic servlet container registration (`/*`). Set to `false` when manually registering the filter within a `SecurityFilterChain`. |
| `ed-iam.security.filter.failure-passthrough` | `false` | When `true`, delegates to `filterChain.doFilter` on token parsing/validation failure instead of immediately returning HTTP 401. |

### Database & Liquibase

| Property | Default | Description |
|---|---|---|
| `ed-iam.liquibase.enabled` | `true` | Enables or disables IAM Liquibase database migrations (`iam_*` tables). Independent from `spring.liquibase.enabled`. |
| `ed-iam.persistence.enabled` | `true` | Controls registration of IAM JPA repositories and entity scanning adapters in `IamSecurityAutoConfiguration`. |

### Management & Scopes

| Property | Default | Description |
|---|---|---|
| `ed-iam.management.enabled` | `true` | Master switch for administrative domain services (`UserManagementService`, etc.). |
| `ed-iam.management.endpoints.enabled` | `true` | Enables or disables IAM management REST controllers (`/api/v1/users`, `/roles`, etc.). |
| `ed-iam.scope.enabled` | `true` | Enables or disables hierarchical scope tree services (`ScopeHierarchyService`, `ScopeSubtreeResolver`). |
| `ed-iam.exception-handler.enabled` | `true` | Controls registration of `IamExceptionHandler` (`@RestControllerAdvice(basePackages = "io.github.edmaputra.iam")`). |

### Authentication Module

| Property | Default | Description |
|---|---|---|
| `ed-iam.auth.enabled` | `true` | Master switch for `IamAuthAutoConfiguration` (authentication services and providers). |
| `ed-iam.auth.endpoints.enabled` | `true` | Enables or disables the core `/api/v1/auth/*` controller endpoints (`/login`, `/refresh`, `/switch-tenant`). |
| `ed-iam.auth.mfa.endpoints.enabled` | `false` | Enables or disables the `/api/v1/auth/mfa/*` controller endpoints. |
| `ed-iam.auth.magic-link.endpoints.enabled` | `false` | Enables or disables the `/api/v1/auth/magic-link/*` controller endpoints. |

---

## 3. Coexistence Scenarios & Recipes

### Scenario A: Host Application with Custom Spring Security

When your host application already configures a `SecurityFilterChain` bean:

#### 1. Configure `application.yml`
```yaml
ed-iam:
  security:
    filter:
      # Keep the bean alive for SecurityFilterChain injection,
      # but prevent Spring Boot from auto-registering it to /*
      register-servlet: false
      # Optional: Allow downstream filters to handle unauthenticated requests
      failure-passthrough: true
  jwt:
    secret-key: "your-256-bit-secret-your-256-bit-secret"
```

#### 2. Inject `JwtAuthenticationFilter` into your `SecurityFilterChain`
```java
package com.example.app.config;

import io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class HostSecurityConfiguration {

    @Bean
    public SecurityFilterChain hostSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Host application public endpoints
                .requestMatchers("/api/public/**", "/health").permitAll()
                // ed-iam public endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            // Position the filter explicitly within the Spring Security chain:
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

---

### Scenario B: Host Application with Existing Liquibase Migrations

If your application already manages its own schema migrations:

#### Option 1: Disable IAM Liquibase Migrations
If your DBA or database administrator manages `iam_*` tables manually, or if you maintain a unified migration changelog:

```yaml
ed-iam:
  liquibase:
    enabled: false # IAM tables are not automatically created or updated
```

#### Option 2: Provide a Custom `iamLiquibase` Bean
`IamLiquibaseAutoConfiguration` uses `@ConditionalOnMissingBean(name = "iamLiquibase")`. You can define a custom `SpringLiquibase` bean to run IAM migrations on a separate datasource or schema:

```java
@Configuration
public class CustomIamLiquibaseConfiguration {

    @Bean(name = "iamLiquibase")
    public SpringLiquibase iamLiquibase(
            @Qualifier("iamDataSource") DataSource iamDataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(iamDataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-iam.json");
        liquibase.setDefaultSchema("iam");
        return liquibase;
    }
}
```

---

### Scenario C: Microservice Consuming Only JWT Validation (No Database)

If a microservice only needs to validate incoming tokens and enforce `@RequirePermission`:

1. In your `pom.xml`, depend **only** on `ed-iam-resource-server`:
```xml
<dependency>
    <groupId>io.github.edmaputra</groupId>
    <artifactId>ed-iam-resource-server</artifactId>
    <version>${ed-iam.version}</version>
</dependency>
```

2. Because `ed-iam-resource-server` contains zero JPA, Liquibase, or database dependencies, no database auto-configuration will activate.

3. In `application.yml`:
```yaml
ed-iam:
  jwt:
    secret-key: "your-256-bit-secret-your-256-bit-secret"
  security:
    filter:
      register-servlet: false # if injecting into custom SecurityFilterChain
```

---

### Scenario D: Using Host Application Exception Handler

By default, `IamExceptionHandler` handles errors thrown by `io.github.edmaputra.iam` controllers (`@RestControllerAdvice(basePackages = "io.github.edmaputra.iam")`). Because it is package-scoped, it **never intercepts host application controllers**.

However, if you want your host application's `@ControllerAdvice` to handle all IAM exceptions as well:

```yaml
ed-iam:
  exception-handler:
    enabled: false
```

Then in your host application:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
    }
}
```

---

## 4. Summary Checklist for Coexistence

When integrating `ed-iam` into an existing Spring Boot application:
- [ ] Set `ed-iam.security.filter.register-servlet: false` if defining a custom `SecurityFilterChain`.
- [ ] Add `jwtAuthenticationFilter` with `.addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`.
- [ ] Set `ed-iam.security.filter.failure-passthrough: true` if using multiple authentication mechanisms.
- [ ] Set `ed-iam.liquibase.enabled: false` if your application manages IAM database schemas externally.
- [ ] Confirm no `spring.autoconfigure.exclude` is required.
