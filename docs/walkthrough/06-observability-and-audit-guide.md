# 06. Observability, Telemetry & SIEM Audit Guide

This guide details the architecture, SIEM security audit event catalog, Micrometer metrics specification, and OpenTelemetry tracing capabilities introduced in Phase 4 of `ed-iam`.

---

## 1. Overview & Architecture

`ed-iam` delivers enterprise-grade observability and security compliance following strict hexagonal design principles:

* **Pure Domain Events (`ed-iam-core`)**: Standardized `IamEvent` records representing business and security state changes.
* **Invariant Validation & Sanitization (`ed-iam-core`)**: The `IamEventValidator` and `ValidatingEventPublisher` enforce structural constraints and scrub sensitive keys (`password`, `secret`, `token`, `credentials`) to `"[PROTECTED]"` before events can be emitted.
* **Structured SIEM Audit Logging (`ed-iam-resource-server`)**: `SecurityAuditEventListener` formats events as either standard key-value logs or JSON payloads to a dedicated `io.github.edmaputra.iam.audit` logger, populating SLF4J MDC context for downstream SIEM ingestion (Splunk, Datadog, Elastic/Logstash, AWS CloudWatch).
* **Native Micrometer Metrics & OpenTelemetry Tracing (`ed-iam-resource-server`)**: `IamTelemetry` instruments authentication latency, attempt rates, JWT validation duration, and access denials with multi-tenant dimensional tags.
* **Unified Facade (`ed-iam-resource-server`)**: `SecurityAuditRecorder` coordinates metrics recording and audit event publishing through clean one-line calls across authentication and security components.

```
       ┌────────────────────────────────────────────────────────┐
       │                 Caller / Domain Service                │
       │    (AuthenticationService, Interceptor, Management)   │
       └───────────────────────────┬────────────────────────────┘
                                   │
                     SecurityAuditRecorder (Facade)
                                   │
           ┌───────────────────────┴───────────────────────┐
           ▼                                               ▼
   IamTelemetry (Micrometer)                     ValidatingEventPublisher
   ├── iam.auth.attempts                         └── IamEventValidator
   ├── iam.auth.latency                              ├── Validates event invariants
   ├── iam.token.validation.time                     └── Scrubs sensitive secrets
   └── iam.access.denied                                   │
                                                           ▼
                                                EventPublisherPort (Spring)
                                                           │
                                                           ▼
                                               SecurityAuditEventListener
                                               ├── MDC Context Tracking
                                               └── Logger: io.github.edmaputra.iam.audit
                                                   ├── KEY_VALUE format
                                                   └── JSON format
```

---

## 2. SIEM Security Audit Event Catalog

All security audit events conform to the immutable record `IamEvent`:

| Field | Type | Description |
| :--- | :--- | :--- |
| `eventId` | `UUID` | Unique UUIDv7 / UUIDv4 identifier for the audit record. |
| `eventType` | `String` | Type constant defined in `IamEventTypes`. |
| `tenantId` | `UUID` | Target tenant identifier (`null` for global/system operations). |
| `entityType` | `String` | Domain entity category (`USER`, `ROLE`, `GROUP`, `SCOPE_NODE`, `ROLE_ASSIGNMENT`, `AUTH`, `SESSION`, `SECURITY`). |
| `entityId` | `UUID` | Primary key of the affected entity (or `00000000-0000-0000-0000-000000000000` for system events). |
| `actor` | `String` | Principal who performed the action (email, user ID, or `"system"` / `"anonymous"`). |
| `correlationId` | `String` | Distributed tracing correlation ID / request ID if present. |
| `occurredAt` | `Instant` | UTC timestamp when the event occurred. |
| `payload` | `Map<String, Object>` | Sanitized, non-sensitive context attributes. |

### Complete Event Catalog by Category

#### A. Authentication & Security Events
| Event Type (`eventType`) | Entity Type | Typical Payload Attributes | Emitted When |
| :--- | :--- | :--- | :--- |
| `LOGIN_SUCCESS` | `AUTH` | `email`, `ip` | A user successfully completes primary authentication (or tenant switch). |
| `LOGIN_FAILED` | `AUTH` | `email`, `reason`, `ip` | Authentication fails due to bad credentials, invalid tokens, or unverified identity. |
| `ACCOUNT_LOCKED` | `AUTH` | `email`, `durationSeconds`, `ip` | User account is locked due to exceeding consecutive failed login thresholds. |
| `ACCOUNT_UNLOCKED` | `AUTH` | `email`, `unlockedBy` | Locked account is reset manually by an administrator or expiry. |
| `ACCESS_DENIED` | `SECURITY` | `reason`, `path`, `requiredPermission`, `requiredPermissions` | Authorization fails at the `@RequirePermission` interceptor or tenant guard level. |

#### B. Session Lifecycle Events
| Event Type (`eventType`) | Entity Type | Typical Payload Attributes | Emitted When |
| :--- | :--- | :--- | :--- |
| `SESSION_CREATED` | `SESSION` | `tokenId`, `ip` | A new active session and refresh token pair are issued. |
| `SESSION_REVOKED` | `SESSION` | `sessionId`, `reason` | A specific user session or refresh token is revoked / logged out. |
| `SESSIONS_REVOKED_ALL` | `SESSION` | `userId`, `revokedCount` | All active sessions for a user are revoked (e.g. status change or logout-all). |

#### C. User Lifecycle Events
| Event Type (`eventType`) | Entity Type | Typical Payload Attributes | Emitted When |
| :--- | :--- | :--- | :--- |
| `USER_CREATED` | `USER` | `email`, `fullName` | New user account is provisioned. |
| `USER_UPDATED` | `USER` | `email`, `fullName` | User profile details are modified. |
| `USER_STATUS_CHANGED` | `USER` | `email`, `status` (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`) | User status changes; automatically triggers session revocation when suspended/deactivated. |
| `USER_PASSWORD_RESET` | `USER` | `email`, `resetMethod` | Password change or administrative reset is executed. |
| `USER_DEACTIVATED` | `USER` | `email` | User account is deactivated or deleted. |

#### D. Role & Permission Events
| Event Type (`eventType`) | Entity Type | Typical Payload Attributes | Emitted When |
| :--- | :--- | :--- | :--- |
| `ROLE_CREATED` | `ROLE` | `code`, `name` | New role definition is registered. |
| `ROLE_UPDATED` | `ROLE` | `code`, `name` | Role metadata or description is updated. |
| `ROLE_MODIFIED` | `ROLE` | `code`, `permissionsAdded`, `permissionsRemoved` | Permissions attached to a role are modified. |
| `ROLE_DELETED` | `ROLE` | `code` | Role definition is removed. |

#### E. Role Assignment Events
| Event Type (`eventType`) | Entity Type | Typical Payload Attributes | Emitted When |
| :--- | :--- | :--- | :--- |
| `ROLE_ASSIGNMENT_CREATED` | `ROLE_ASSIGNMENT` | `userId`, `roleId`, `scopeNodeId` (if scoped) | A role is granted to a user tenant-wide or within an organizational scope subtree. |
| `ROLE_ASSIGNMENT_REVOKED` | `ROLE_ASSIGNMENT` | `userId`, `roleId` | A role grant is revoked from a user. |

#### F. Group & Membership Events
| Event Type (`eventType`) | Entity Type | Typical Payload Attributes | Emitted When |
| :--- | :--- | :--- | :--- |
| `GROUP_CREATED` | `GROUP` | `code`, `name` | New user group is created. |
| `GROUP_UPDATED` | `GROUP` | `code`, `name` | Group metadata is modified. |
| `GROUP_DELETED` | `GROUP` | `code` | Group is deleted. |
| `GROUP_MEMBERSHIP_ADDED` | `GROUP` | `groupId`, `userId` | User is assigned membership in a group. |
| `GROUP_MEMBERSHIP_REMOVED` | `GROUP` | `groupId`, `userId` | User is removed from a group. |

#### G. Scope Hierarchy Events
| Event Type (`eventType`) | Entity Type | Typical Payload Attributes | Emitted When |
| :--- | :--- | :--- | :--- |
| `SCOPE_NODE_CREATED` | `SCOPE_NODE` | `code`, `name`, `parentId`, `path` | New hierarchical node (hospital, clinic, department) is added. |
| `SCOPE_NODE_UPDATED` | `SCOPE_NODE` | `code`, `name` | Scope node metadata is modified. |
| `SCOPE_NODE_MOVED` | `SCOPE_NODE` | `oldParentId`, `newParentId`, `newPath` | Scope subtree is re-parented with cascade path updates. |
| `SCOPE_NODE_DELETED` | `SCOPE_NODE` | `code`, `path` | Scope node is deleted. |

---

## 3. Credential Sanitization & Invariant Protection

To guarantee that credentials or API tokens never leak into SIEM log streams, `IamEventValidator` automatically sanitizes event payloads.

### Scrubbed Payload Keys
Any payload key matching (case-insensitively):
* `password`
* `secret`
* `rawPassword`
* `clientSecret`
* `token`
* `accessToken`
* `refreshToken`
* `authorization`
* `credentials`

Is automatically replaced with the string:
```
"[PROTECTED]"
```

---

## 4. Micrometer Telemetry Metrics Specification

All metrics are registered with Spring Boot's standard `MeterRegistry` under the `iam.*` namespace.

### Metric 1: Authentication Attempts Counter
* **Meter Name**: `iam.auth.attempts`
* **Type**: `Counter`
* **Description**: Total number of authentication attempts processed by the IAM system.
* **Tags**:
  * `auth_type`: The authentication mechanism (`password`, `api_key`, `oidc`, `magic_link`, `refresh_token`).
  * `status`: Outcome of the attempt (`success`, `failure`, `locked`, `mfa_required`).
  * `tenant_id`: Target tenant UUID, or `"anonymous"` if unauthenticated / global.

### Metric 2: Authentication Latency Timer
* **Meter Name**: `iam.auth.latency`
* **Type**: `Timer`
* **Description**: Distribution and latency of authentication requests (tracks throughput, mean, max, percentiles).
* **Tags**:
  * `auth_type`: Authentication mechanism (`password`, `api_key`, `oidc`, `magic_link`, `refresh_token`).
  * `status`: Outcome (`success`, `failure`, `locked`, `mfa_required`).
  * `tenant_id`: Target tenant UUID, or `"anonymous"`.

### Metric 3: JWT Token Validation Timer
* **Meter Name**: `iam.token.validation.time`
* **Type**: `Timer`
* **Description**: Time taken by `JwtAuthenticationFilter` to parse, cryptographically verify, check revocation status, and establish the `ScopedValue` context.
* **Tags**:
  * `status`: Validation result:
    * `valid`: Signature verified, unexpired, and not revoked.
    * `expired`: Token signature valid but expired.
    * `revoked`: Token listed in distributed/in-memory token revocation store.
    * `invalid`: Malformed, unrecognized issuer, or invalid signature.

### Metric 4: Access Denied Counter
* **Meter Name**: `iam.access.denied`
* **Type**: `Counter`
* **Description**: Total number of unauthorized or forbidden access denial events.
* **Tags**:
  * `reason`: Reason category (`missing_permission`, `scope_denied`, `bad_credentials`, `account_locked`, `tenant_mismatch`, `token_revoked`, `token_expired`).
  * `permission`: The requested permission string (e.g. `iam:role:create`, `patient:write`), or `"none"`.
  * `tenant_id`: Target tenant UUID, or `"anonymous"`.
  * `actor`: Identity of the caller (user UUID, email, or `"anonymous"`).

---

## 5. OpenTelemetry Distributed Tracing

`IamTelemetry` integrates with standard OpenTelemetry tracers via the instrumentation scope:
```
io.github.edmaputra.iam
```

### Supported Operations:
* `startSpan(String spanName)`: Starts an active OpenTelemetry span.
* `executeInSpan(String spanName, Supplier<T> operation)`: Executes the lambda within an automatic span lifecycle, attaching exception status codes and recording stack traces on failure.

---

## 6. Configuration Reference

All observability features are configurable via `application.yml`:

```yaml
iam:
  # SIEM Security Audit Logging
  audit:
    enabled: true                 # Master switch for SIEM audit event listener (default: true)
    format: KEY_VALUE             # Output format: KEY_VALUE or JSON (default: KEY_VALUE)
    include-payload: true         # Whether to serialize event context payload (default: true)
    mdc-enabled: true             # Whether to populate SLF4J MDC attributes (default: true)

  # Telemetry & Metrics
  telemetry:
    enabled: true                 # Master switch for IAM telemetry (default: true)
    metrics-enabled: true         # Enable Micrometer meters under iam.* (default: true)
    tracing-enabled: true         # Enable OpenTelemetry span generation (default: true)
```

### SIEM Log Formatting Examples

#### Standard Key-Value Format (`format: KEY_VALUE`):
```text
2026-10-05T11:52:26.037+07:00 INFO [ed-iam-playground] [o-auto-1-exec-7] io.github.edmaputra.iam.audit : IAM_AUDIT eventType=LOGIN_SUCCESS tenantId=11111111-1111-1111-1111-111111111111 entityType=AUTH entityId=01a10a67-eeab-7d0a-92ba-464294d448f0 actor=custom-cardiologist@metro.org correlationId=null occurredAt=2026-10-05T04:52:26.037457Z payload={email=custom-cardiologist@metro.org, ip=127.0.0.1}
```

#### JSON Format (`format: JSON`):
```json
{
  "eventId": "01a10a67-eeab-7d0a-92ba-464294d448f0",
  "eventType": "LOGIN_SUCCESS",
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "entityType": "AUTH",
  "entityId": "01a10a67-eeab-7d0a-92ba-464294d448f0",
  "actor": "custom-cardiologist@metro.org",
  "correlationId": null,
  "occurredAt": "2026-10-05T04:52:26.037457Z",
  "payload": {
    "email": "custom-cardiologist@metro.org",
    "ip": "127.0.0.1"
  }
}
```

#### MDC Attributes Populated During Logging:
* `iam.event.id`: Unique event UUID.
* `iam.event.type`: Event type constant.
* `iam.tenant.id`: Target tenant UUID (or empty).
* `iam.actor`: Actor identifier.
* `iam.correlation.id`: Request correlation ID.
