package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AccountLockedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Focused application service responsible for authenticating credentials via configured providers
 * and enforcing brute-force lockout defenses.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class CredentialAuthService {

	private static final Logger log = LoggerFactory.getLogger(CredentialAuthService.class);

	private final AuthenticationProviderRouter authRouter;
	private final LoginAttemptTrackerPort loginAttemptTracker;
	private final SecurityAuditRecorder auditRecorder;

	public CredentialAuthService(
			AuthenticationProviderRouter authRouter,
			LoginAttemptTrackerPort loginAttemptTracker,
			SecurityAuditRecorder auditRecorder) {
		this.authRouter = Objects.requireNonNull(authRouter, "AuthenticationProviderRouter must not be null.");
		this.loginAttemptTracker = loginAttemptTracker;
		this.auditRecorder = auditRecorder != null ? auditRecorder : SecurityAuditRecorder.noop();
	}

	public CredentialAuthService(AuthenticationProviderRouter authRouter) {
		this(authRouter, null, null);
	}

	public AuthenticatedIdentity authenticatePassword(
			String email,
			String password,
			TenantId tenantId,
			String ipAddress,
			long startTime) {
		if (loginAttemptTracker != null) {
			LockoutStatus status = loginAttemptTracker.getLockoutStatus(email);
			if (status != null && status.locked()) {
				auditRecorder.recordAccountLocked("password", email, tenantId != null ? tenantId.value() : null, status.lockedUntil().toString(), startTime);
				log.warn("Login rejected: account for '{}' is locked until {}", email, status.lockedUntil());
				throw new AccountLockedException(
						"Account is temporarily locked due to too many failed attempts until: " + status.lockedUntil(),
						status.lockedUntil());
			}
		}

		try {
			AuthenticatedIdentity identity = authRouter.authenticate(
					new PasswordAuthCredentials(email, password));
			if (loginAttemptTracker != null) {
				loginAttemptTracker.recordSuccessfulAttempt(email);
			}
			return identity;
		}
		catch (AuthenticationException ex) {
			auditRecorder.recordLoginFailure("password", email, tenantId != null ? tenantId.value() : null, "bad_credentials", ex.getMessage(), ipAddress, startTime);
			log.warn("Authentication failed for principal '{}': {}", email, ex.getMessage());
			if (loginAttemptTracker != null) {
				loginAttemptTracker.recordFailedAttempt(email, Instant.now());
			}
			throw ex;
		}
	}
}
