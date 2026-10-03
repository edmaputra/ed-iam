package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.application.port.in.ManageLockoutUseCase;
import io.github.edmaputra.iam.application.port.in.ManageSessionUseCase;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link ManageSessionUseCase} and {@link ManageLockoutUseCase}.
 * Provides administrative session tracking, revocation, and brute-force lockout remediation.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class SessionManagementService implements ManageSessionUseCase, ManageLockoutUseCase {

	private final UserRepository userRepository;
	private final SessionRegistryPort sessionRegistry;
	private final TokenRevocationPort tokenRevocationPort;
	private final LoginAttemptTrackerPort loginAttemptTracker;

	public SessionManagementService(
			UserRepository userRepository,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			LoginAttemptTrackerPort loginAttemptTracker) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.sessionRegistry = sessionRegistry;
		this.tokenRevocationPort = tokenRevocationPort;
		this.loginAttemptTracker = loginAttemptTracker;
	}

	@Override
	public List<UserSession> listUserSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		verifyUserExists(userId);
		if (sessionRegistry == null) {
			return List.of();
		}
		return sessionRegistry.findActiveSessions(userId, tenantId);
	}

	@Override
	public void terminateSession(UserId userId, SessionId sessionId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(sessionId, "SessionId must not be null.");
		verifyUserExists(userId);

		if (sessionRegistry != null) {
			Optional<UserSession> sessionOpt = sessionRegistry.findSession(sessionId);
			if (sessionOpt.isPresent()) {
				UserSession session = sessionOpt.get();
				if (!session.userId().equals(userId)) {
					throw new IllegalArgumentException("Session " + sessionId + " does not belong to user " + userId.value());
				}
				sessionRegistry.revokeSession(sessionId);
				if (tokenRevocationPort != null && session.tokenIdentifier() != null) {
					tokenRevocationPort.revokeToken(session.tokenIdentifier(), session.expiresAt());
				}
			}
		}
	}

	@Override
	public void terminateAllUserSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		verifyUserExists(userId);

		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeAllForUser(userId, Instant.now());
		}
		if (sessionRegistry != null) {
			List<UserSession> activeSessions = sessionRegistry.findActiveSessions(userId, tenantId);
			for (UserSession s : activeSessions) {
				sessionRegistry.revokeSession(s.id());
				if (tokenRevocationPort != null && s.tokenIdentifier() != null) {
					tokenRevocationPort.revokeToken(s.tokenIdentifier(), s.expiresAt());
				}
			}
		}
	}

	@Override
	public LockoutStatus getLockoutStatus(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		User user = verifyUserExists(userId);
		if (loginAttemptTracker == null) {
			return LockoutStatus.unlocked(0);
		}
		return loginAttemptTracker.getLockoutStatus(user.getEmail());
	}

	@Override
	public void unlockUser(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		User user = verifyUserExists(userId);
		if (loginAttemptTracker != null) {
			loginAttemptTracker.unlock(user.getEmail());
		}
	}

	private User verifyUserExists(UserId userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException(userId));
	}
}
