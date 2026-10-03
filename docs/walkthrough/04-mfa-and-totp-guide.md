# 04. Multi-Factor Authentication (MFA / TOTP) Guide

This guide details the architecture, cryptographic mechanics, and API lifecycle of the **RFC 6238 Time-Based One-Time Password (TOTP)** Multi-Factor Authentication subsystem in `ed-iam`.

---

## 1. Overview & Architecture Standards

`ed-iam` delivers multi-factor authentication as an enterprise-grade security layer following strict hexagonal architecture principles:

* **Pure Domain Kernel (`ed-iam-core`)**: Zero third-party library dependencies. The mathematical engine (`TotpGenerator`) and domain models (`UserMfa`) are implemented using standard Java standard library cryptographic primitives (`javax.crypto.Mac`, `java.security.MessageDigest`, `java.security.SecureRandom`).
* **Cryptographic Standards**:
  * **RFC 6238**: Time-Based One-Time Password Algorithm (TOTP).
  * **RFC 4226**: HMAC-Based One-Time Password Algorithm (HOTP).
  * **RFC 4648**: Base-N Data Encodings (Base32 alphabet without padding).
* **Two-Step Challenge Flow**: User authentication separates password verification from MFA completion via transient, cryptographically signed challenge JWT tokens.
* **Single-Use Emergency Backup Codes**: Account recovery codes are hashed with SHA-256 and salted with the user's secret before storage, ensuring zero plain-text storage and single-use consumption.

---

## 2. Cryptographic Engine Mechanics (`TotpGenerator`)

```
                  ┌──────────────────────┐
                  │ Current Time (Epoch) │
                  └──────────┬───────────┘
                             │  Floor Divide by 30 seconds
                             ▼
┌──────────────────┐    ┌──────────┐
│ Base32 Secret    ├───►│ HMAC-    │───► 20-byte Hash
│ (Decoded bytes)  │    │ SHA1     │           │
└──────────────────┘    └──────────┘           ▼
                                      Dynamic Truncation
                                      (Read 4 bytes at offset)
                                               │
                                               ▼
                                      Binary modulo 1,000,000
                                               │
                                               ▼
                                      6-digit Code (e.g., "507360")
```

### A. Time-Step Counter Calculation
Time is partitioned into discrete intervals:
$$\text{Time Step } T = \left\lfloor \frac{\text{Epoch Seconds}}{30} \right\rfloor$$
Every 30 seconds, $T$ increments by 1. Both the server and the user's authenticator app (Google Authenticator, Microsoft Authenticator, 1Password) compute this exact value synchronously based on UTC epoch timestamps.

### B. RFC 4648 Base32 Encoding & Decoding
Authenticator applications consume secret keys encoded in Base32 (32 alphanumeric characters: `A-Z` and `2-7`), which avoids easily confusable characters (`0`, `O`, `1`, `I`, `8`, `B`). `TotpGenerator` provides a custom, allocation-efficient Base32 encoder and decoder without external dependencies.

### C. HMAC-SHA1 Computation
The 8-byte big-endian binary representation of time step $T$ is hashed using `HmacSHA1` keyed with the decoded 160-bit (20-byte) secret:
```java
Mac mac = Mac.getInstance("HmacSHA1");
mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
byte[] hash = mac.doFinal(timeBytes); // 20-byte hash
```

### D. Dynamic Truncation (Extracting 6 Digits)
Conforming to RFC 4226 §5.4:
1. The low-order 4 bits of the last byte determine the extraction offset: `offset = hash[19] & 0x0F` (yielding a range of 0 to 15).
2. Four consecutive bytes starting at `hash[offset]` are read as a big-endian 31-bit unsigned integer:
   ```java
   int binary = ((hash[offset] & 0x7F) << 24)
           | ((hash[offset + 1] & 0xFF) << 16)
           | ((hash[offset + 2] & 0xFF) << 8)
           | (hash[offset + 3] & 0xFF);
   ```
3. Modulo $10^6$ extracts the final 6 digits:
   ```java
   int otp = binary % 1_000_000;
   return String.format("%06d", otp);
   ```

### E. Clock Drift Tolerance Window
Network lag or device clock desynchronization is handled by checking a window of steps ($\pm 1$ step = past 30s, current 30s, and next 30s). To prevent side-channel timing attacks, candidate codes are compared using `MessageDigest.isEqual(...)`.

### F. Authenticator QR Provisioning (`otpauth://`)
Standard URIs recognized by camera scanners are constructed:
```
otpauth://totp/{issuer}:{email}?secret={base32Secret}&issuer={issuer}&algorithm=SHA1&digits=6&period=30
```

---

## 3. Endpoints & Lifecycle Guide

The MFA subsystem exposes five endpoints under `/api/v1/auth/mfa/*`:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MFA API Lifecycle Flow                          │
└────────────────────────────────────────────────────────────────────────┘

  [Enrollment Phase]
  User (Logged in) ──► GET  /api/v1/auth/mfa/status   (returns enabled: false)
  User             ──► POST /api/v1/auth/mfa/setup    (returns secret, QR URI, backup codes)
  User             ──► POST /api/v1/auth/mfa/activate (validates initial code; enables MFA)

  [Authentication Phase]
  User             ──► POST /api/v1/auth/login        (returns mfaRequired: true + mfaToken)
  User             ──► POST /api/v1/auth/mfa/verify   (submits TOTP or Backup Code)
                                                      (returns full access & refresh tokens)

  [Management Phase]
  User (Logged in) ──► POST /api/v1/auth/mfa/disable  (requires TOTP code or password)
```

### 1. `GET /api/v1/auth/mfa/status`
* **When**: When loading the user's Account Settings or Security Profile page.
* **Why**: Allows the UI to conditionally render the "Enable 2FA" enrollment wizard or the "2FA Active / Disable" management card.
* **Security**: Requires an active `Bearer` access token.

### 2. `POST /api/v1/auth/mfa/setup`
* **When**: When the user clicks "Enable Two-Factor Authentication".
* **Why**: Generates a new 160-bit Base32 secret, the `otpauth://` QR URI, and 8 emergency backup codes. MFA remains **disabled** until the user confirms they can generate valid codes.
* **Response Payload**:
  ```json
  {
    "secret": "JBSWY3DPEHPK3PXP...",
    "qrCodeUri": "otpauth://totp/...",
    "backupCodes": [
      "A1B2-C3D4",
      "E5F6-G7H8",
      ...
    ]
  }
  ```

### 3. `POST /api/v1/auth/mfa/activate`
* **When**: Immediately after scanning the QR code, the user enters the first 6-digit code shown in their authenticator app.
* **Why**: **Lockout Prevention.** Protects users from locking themselves out if they scanned incorrectly or if their app failed to store the secret. Once verified, `is_enabled` is set to `true`.
* **Request Payload**:
  ```json
  { "code": "507360" }
  ```
* **Response**: `204 No Content`.

### 4. Two-Step Login Challenge (`POST /api/v1/auth/login`)
* **When**: When a user with MFA enabled logs in with their primary credentials (email and password).
* **Why**: Prevents issuing full JWT access tokens until the second factor is verified.
* **Response Payload**:
  ```json
  {
    "mfaRequired": true,
    "mfaToken": "eyJhbGciOiJIUzUxMiJ9...",
    "accessToken": null,
    "refreshToken": null
  }
  ```
* **MFA Challenge Token Details**:
  * Signed with the system HS512 secret.
  * Contains `tokenType: "MFA_CHALLENGE"`.
  * Short-lived TTL: 300 seconds (5 minutes).
  * **Cannot** be used to access regular protected resources; any attempt is rejected with `401 Unauthorized`.

### 5. `POST /api/v1/auth/mfa/verify`
* **When**: The user submits their 6-digit TOTP code or an 8-character single-use backup recovery code.
* **Why**: Validates the challenge token and exchanges it for a full `TokenResponse` (access token, refresh token, and user profile).
* **Backup Recovery Code Handling**:
  * If the user enters a backup code (e.g. `A1B2-C3D4`), the server computes `SHA-256(secret + ":" + code)`.
  * If a matching hash is found, that hash is **permanently consumed and removed** from `iam_user_mfa`.
  * Attempting to replay the same code in a subsequent challenge returns `401 Unauthorized`.
* **Request Payload**:
  ```json
  {
    "mfaToken": "eyJhbGciOiJIUzUxMiJ9...",
    "code": "507360"
  }
  ```

### 6. `POST /api/v1/auth/mfa/disable`
* **When**: When a user turns off two-factor authentication or switches to a new authenticator device.
* **Why**: Disabling MFA reduces account security. To prevent unauthorized disablement (e.g. from an unattended workstation), the caller must prove possession by supplying either their **current TOTP code** or their **account password**.
* **Request Payload**:
  ```json
  { "codeOrPassword": "P@ssw0rd123!" }
  ```
* **Response**: `204 No Content`.

---

## 4. Database Schema & Persistence

The MFA configuration is persisted in the isolated `iam_user_mfa` table via Liquibase:

```sql
CREATE TABLE iam_user_mfa (
    id           UUID         NOT NULL PRIMARY KEY,
    user_id      UUID         NOT NULL UNIQUE,
    secret       VARCHAR(64)  NOT NULL,
    is_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    backup_codes TEXT,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_iam_user_mfa_user FOREIGN KEY (user_id) REFERENCES iam_user (id) ON DELETE CASCADE
);
```

### Key Design Highlights:
* **One-to-One Isolation**: Keeps secret material separated from the core `iam_user` record.
* **Hashed Backup Codes**: The `backup_codes` column stores a comma-separated list of SHA-256 hashes rather than plain text.
* **Lifecycle Cascades**: Deleting an account through `UserManagementService` automatically deletes the associated `iam_user_mfa` record.

---

## 5. Security & Threat Mitigation

| Threat Vector | Mitigation Strategy in `ed-iam` |
|:---|:---|
| **Timing Attacks** | Constant-time array comparison (`MessageDigest.isEqual`) on all code verifications. |
| **Token Replay & Hijacking** | MFA challenge tokens carry `tokenType = MFA_CHALLENGE` with a strict 300s TTL. Regular endpoints reject challenge tokens, and the MFA verification endpoint rejects access/refresh tokens. |
| **Brute-Force Guessing** | Failed verification attempts are tracked by `LoginAttemptTrackerPort`, locking the user account or IP after reaching the configured attempt threshold. |
| **Backup Code Replay** | Verified backup codes are atomically removed from persistence upon first use (`consumeBackupCode`). |
| **Accidental Lockout** | Activation requires entering a valid code generated by the newly enrolled secret before `is_enabled` becomes `true`. |
| **Unauthorized Deactivation** | Disabling MFA requires re-authenticating with either a live TOTP code or account password. |

---

## 6. Testing & Manual Verification

### Automated Integration Test
The reference application `ed-iam-playground` contains a complete end-to-end integration test:
* `PlaygroundApplicationTests.java` (`shouldSupportMfaLifecycleAndEnforceLoginChallenge`).

### Manual HTTP Client Testing
Run interactive requests using `samples/ed-iam-playground/playground-requests.http` under Section 8:
* `8.1 Check MFA Status`
* `8.2 Initiate MFA Setup`
* `8.3 Activate MFA`
* `8.4 Login Challenged by MFA`
* `8.5 Verify MFA Login Challenge with TOTP Code`
* `8.6 Verify MFA Login Challenge with Backup Code`
* `8.7 Disable MFA with TOTP or Password`
