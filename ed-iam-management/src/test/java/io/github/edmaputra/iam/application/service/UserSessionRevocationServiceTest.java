package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionRevocationServiceTest {

	@Mock
	private SessionRegistryPort sessionRegistry;

	@Mock
	private TokenRevocationPort tokenRevocationPort;

	private UserSessionRevocationService sessionRevocationService;

	@BeforeEach
	void setUp() {
		sessionRevocationService = new UserSessionRevocationService(sessionRegistry, tokenRevocationPort);
	}

	@Test
	@DisplayName("Should revoke all user sessions and tokens")
	void shouldRevokeAllUserSessions() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		SessionId sessionId = SessionId.generate();
		Instant expiresAt = Instant.now().plusSeconds(3600);

		UserSession session = UserSession.create(
				userId,
				tenantId,
				"token-id-123",
				3600L,
				"127.0.0.1",
				"Test-Agent");

		when(sessionRegistry.findActiveSessions(userId, null)).thenReturn(List.of(session));

		sessionRevocationService.revokeAllUserSessions(userId);

		verify(tokenRevocationPort).revokeAllForUser(eq(userId), any(Instant.class));
		verify(sessionRegistry).revokeSession(session.id());
		verify(tokenRevocationPort).revokeToken("token-id-123", session.expiresAt());
	}
}
