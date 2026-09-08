# ed-iam Playground

Interactive Reference Application & IAM Management Console demonstrating **`ed-iam`** (Multi-Tenant IAM Spring Boot Starter for Java 25 and Spring Boot 4.x).

---

## Quick Start

### 1. Run with Docker (Recommended)

Run the pre-built container directly from GitHub Container Registry:

```bash
docker run -d --name ed-iam-playground -p 8080:8080 ghcr.io/edmaputra/ed-iam-playground:latest
```

Open your browser at: **[http://localhost:8080](http://localhost:8080)**

### 2. Run with Docker Compose

From within the `samples/ed-iam-playground` directory:

```bash
docker compose up -d
```

To run with an external PostgreSQL database container instead of the in-memory H2 database:

```bash
docker compose --profile postgres up -d
```

### 3. Run Locally with Maven

From the root of the repository:

```bash
./mvnw spring-boot:run -pl samples/ed-iam-playground
```

---

## Pre-Seeded Personas

All pre-seeded accounts share the default password: **`P@ssw0rd123!`**

| Persona | Email | Roles & Permissions | Scope Boundary | Purpose |
|---|---|---|---|---|
| **Clinician (Doctor)** | `doctor@metro.org` | `CLINICIAN` (`PATIENT_READ`, `PATIENT_WRITE`) | Metro Hospital > Cardiology | Demonstrates scoped access enforcement and patient record modifications. |
| **Multi-Tenant Consultant** | `consultant@healthgroup.org` | `CLINICIAN` across multiple tenants | Global / Cross-Clinic | Demonstrates tenant auto-resolution and dynamic tenant context switching (`/api/v1/auth/switch-tenant`). |
| **Hospital Administrator** | `admin@metro.org` | `TENANT_ADMIN` (`*` wildcard permissions) | Metro Hospital (Root) | Demonstrates full administrative console capabilities, user provisioning, role assignments, and tree reparenting. |
| **Nurse (Group Role Inheritor)** | `nurse@metro.org` | Member of `Surgical Team` | Metro Hospital > Surgery | Demonstrates dynamic role and permission inheritance via group membership. |
| **Suspended Staff** | `suspended@metro.org` | `CLINICIAN` | Metro Hospital | Demonstrates account lifecycle enforcement (denied access on suspension). |

---

## Features Demonstrated

1. **Interactive IAM Management Console**:
   - **Users & Lifecycle**: Provision users, view effective access, toggle active/suspended status.
   - **Roles & Permissions**: Manage granular permissions and custom roles.
   - **Groups & Teams**: Manage user groups and group-level role inheritance.
   - **Scopes & Tree Hierarchy**: Visualize organizational scope trees and subtree navigation.
   - **Auth & Tokens**: Inspect JWT claims, access tokens, and refresh tokens.
2. **1-Click Demo Scenarios**:
   - Scope Boundary Enforcement (Cardiology vs Oncology record isolation).
   - Group Role Inheritance (`Surgical Team` -> `SURGICAL_NURSE` role).
   - Account Suspension Safeguards.
   - Dynamic Multi-Tenant Context Switching.
3. **Container Readiness & Monitoring**:
   - Production health probe: `GET /actuator/health`
   - Application info: `GET /actuator/info`
   - HTTP requests reference: [playground-requests.http](playground-requests.http)
