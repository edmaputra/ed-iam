package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.exception.AccountLockedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CredentialAuthService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class CredentialAuthServiceTest {

	private AuthenticationProviderRouter authRouter;
	private LoginAttemptTrackerPort loginAttemptTracker;
	private SecurityAuditRecorder auditRecorder;

	private CredentialAuthService service;

	@BeforeEach
	void setUp() {
		authRouter = mock(AuthenticationProviderRouter.class);
		loginAttemptTracker = mock(LoginAttemptTrackerPort.class);
		auditRecorder = mock(SecurityAuditRecorder.class);

		service = new CredentialAuthService(authRouter, loginAttemptTracker, auditRecorder);
	}

	@Test
	@DisplayName("Should successfully authenticate password and record successful attempt")
	void shouldAuthenticatePassword() {
		UserId userId = UserId.generate();
		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "user@test.org", "User", false, ProviderType.LOCAL);

		when(authRouter.authenticate(any(PasswordAuthCredentials.class))).thenReturn(identity);
		when(loginAttemptTracker.getLockoutStatus("user@test.org")).thenReturn(LockoutStatus.unlocked(0));

		AuthenticatedIdentity result = service.authenticatePassword("user@test.org", "pass", TenantId.generate(), "127.0.0.1", System.nanoTime());

		assertThat(result).isSameAs(identity);
		verify(loginAttemptTracker).recordSuccessfulAttempt("user@test.org");
	}

	@Test
	@DisplayName("Should reject authentication when account is locked")
	void shouldRejectWhenLocked() {
		Instant lockedUntil = Instant.now().plusSeconds(600);
		when(loginAttemptTracker.getLockoutStatus("locked@test.org")).thenReturn(LockoutStatus.locked(5, lockedUntil));

		assertThatThrownBy(() -> service.authenticatePassword("locked@test.org", "pass", null, "127.0.0.1", System.nanoTime()))
				.isInstanceOf(AccountLockedException.class)
				.hasMessageContaining("Account is temporarily locked");
	}

	@Test
	@DisplayName("Should record failed attempt on AuthenticationException")
	void shouldRecordFailedAttempt() {
		when(loginAttemptTracker.getLockoutStatus("fail@test.org")).thenReturn(LockoutStatus.unlocked(0));
		when(authRouter.authenticate(any(PasswordAuthCredentials.class))).thenThrow(new AuthenticationException("Bad credentials"));

		assertThatThrownBy(() -> service.authenticatePassword("fail@test.org", "wrong", null, "127.0.0.1", System.nanoTime()))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Bad credentials");

		verify(loginAttemptTracker).recordFailedAttempt(eq("fail@test.org"), any(Instant.class));
	}
}
