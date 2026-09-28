# 02. Getting Started as a Contributor

This guide helps you set up your local development environment, build the project, run test suites (including architecture tests), and explore the interactive playground.

---

## 1. Prerequisites

Before contributing to `ed-iam`, ensure your machine has:
* **Java 25 JDK** (OpenJDK 25, Eclipse Temurin, or equivalent). Verify via `java -version`.
* **Git 2.40+**.
* **Docker / Docker Compose** (Optional, for running pre-built images and local Postgres if needed).
* **Maven Wrapper**: The project bundles `./mvnw`, so a separate Maven installation is not required.

```bash
# Verify Java version
java -version
# Expected: openjdk version "25" ...
```

---

## 2. Building the Project

The project is structured as a multi-module Maven project. The Maven wrapper is located at the repository root.

### Build and Package (Skip Tests for Quick Compilation)
```bash
./mvnw clean package -DskipTests
```

### Full Build and Test Suite Execution
```bash
./mvnw test
```

### Full Verification & JaCoCo Coverage Check
```bash
./mvnw clean verify
```
This runs all unit tests, integration tests, Spring context loading tests, and ArchUnit architectural compliance rules.

---

## 3. Running the Interactive Playground

`samples/ed-iam-playground` is a reference Spring Boot application that demonstrates `ed-iam` in a multi-tenant clinical setting.

### Option A: Run Locally via Maven
```bash
./mvnw spring-boot:run -pl samples/ed-iam-playground
```

### Option B: Run via Pre-Built Docker Image
```bash
docker run -d --name ed-iam-playground -p 8080:8080 ghcr.io/edmaputra/ed-iam-playground:latest
```

Once started:
1. Open [http://localhost:8080](http://localhost:8080) in your browser.
2. Explore pre-seeded personas:
   * **St. Jude Hospital Tenant**: Admin, Senior Clinician, Nurse.
   * **Metro General Hospital Tenant**: Admin, Clinician.
3. Test custom credential login, token inspection, switching active tenant context, and path-indexed departmental scoping.

---

## 4. Contributor Standards & Checklist

When contributing code, adhere to the following rules:

### 1. Hexagonal Domain Purity 🔴 MUST
* Never import Spring, Spring Boot, JPA, Hibernate, or Jakarta Web packages inside `ed-iam-core`'s `domain` package.
* Keep domain models as pure Java with compact record validation.
* Run `./mvnw test -pl ed-iam-core` to verify `DomainArchitectureTest` passes.

### 2. Immutability
* Use Java `record` types for all DTOs, value objects, commands, and domain events.
* Ensure domain collections are immutable (e.g., `Collections.unmodifiableSet` or `Set.copyOf`).

### 3. Javadoc Standards 🔴 MUST
* Every newly introduced top-level class, interface, record, or enum **MUST** include:
  ```java
  /**
   * Brief description of the class responsibility.
   *
   * @author edmaputra
   * @since 0.1.0
   */
  ```

### 4. Schema Isolation
* If adding new database tables or columns, add them to `ed-iam-management/src/main/resources/db/changelog/iam/`.
* Tables **MUST** start with the `iam_` prefix.
* Do not assume or couple foreign keys with external host-application tables.

### 5. Git & Commit Guidelines
* Follow [Conventional Commits](https://www.conventionalcommits.org/):
  * `feat(core): add support for tenant-level wildcard permissions`
  * `fix(resource-server): handle null scopes in CurrentActor`
  * `docs(walkthrough): update module class catalog`
