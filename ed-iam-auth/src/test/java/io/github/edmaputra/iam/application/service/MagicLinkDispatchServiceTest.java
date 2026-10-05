package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MagicLinkDispatchService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@ExtendWith(MockitoExtension.class)
class MagicLinkDispatchServiceTest {

	@Mock
	private MagicLinkTokenStorePort tokenStore;

	@Mock
	private MagicLinkNotifierPort notifier;

	@Mock
	private UserRepository userRepository;

	private MagicLinkProperties properties;
	private MagicLinkDispatchService dispatchService;

	@BeforeEach
	void setUp() {
		properties = new MagicLinkProperties(true, 900L, "http://localhost:8080/");
		dispatchService = new MagicLinkDispatchService(properties, tokenStore, notifier);
	}

	@Test
	@DisplayName("Should require non-null mandatory dependencies in constructor")
	void shouldValidateConstructorParameters() {
		assertThatThrownBy(() -> new MagicLinkDispatchService(properties, null, notifier))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MagicLinkTokenStorePort");

		assertThatThrownBy(() -> new MagicLinkDispatchService(properties, tokenStore, null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MagicLinkNotifierPort");
	}

	@Test
	@DisplayName("Should throw AuthenticationException when magic link authentication is disabled")
	void shouldThrowWhenDisabled() {
		MagicLinkProperties disabled = new MagicLinkProperties(false, 900L, "http://localhost:8080");
		MagicLinkDispatchService disabledService = new MagicLinkDispatchService(disabled, tokenStore, notifier);

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@clinic.org");
		assertThatThrownBy(() -> disabledService.requestMagicLink(command, userRepository))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("currently disabled");
	}

	@Test
	@DisplayName("Should successfully issue token and dispatch email when user exists and is active")
	void shouldIssueTokenAndDispatchEmail() {
		TenantId tenantId = TenantId.generate();
		User user = new User(UserId.generate(), "user@clinic.org", "hash", "Active User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("user@clinic.org")).thenReturn(Optional.of(user));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@clinic.org", tenantId, "https://app.clinic.org/dashboard");
		MagicLinkRequestResponse response = dispatchService.requestMagicLink(command, userRepository);

		assertThat(response).isNotNull();
		assertThat(response.message()).contains("sign-in link has been sent");

		ArgumentCaptor<MagicLinkToken> tokenCaptor = ArgumentCaptor.forClass(MagicLinkToken.class);
		verify(tokenStore).save(tokenCaptor.capture());

		MagicLinkToken savedToken = tokenCaptor.getValue();
		assertThat(savedToken.userId()).isEqualTo(user.getId());
		assertThat(savedToken.tenantId()).isEqualTo(tenantId);
		assertThat(savedToken.email()).isEqualTo("user@clinic.org");

		ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
		verify(notifier).sendMagicLink(eq(savedToken), urlCaptor.capture());

		String sentUrl = urlCaptor.getValue();
		assertThat(sentUrl).startsWith("http://localhost:8080/api/v1/auth/magic-link/verify?token=");
		assertThat(sentUrl).contains("&redirect=https%3A%2F%2Fapp.clinic.org%2Fdashboard");
	}

	@Test
	@DisplayName("Should return generic response without dispatching when user is not found")
	void shouldReturnGenericResponseWhenUserNotFound() {
		when(userRepository.findByEmail("unknown@clinic.org")).thenReturn(Optional.empty());

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("unknown@clinic.org");
		MagicLinkRequestResponse response = dispatchService.requestMagicLink(command, userRepository);

		assertThat(response.message()).contains("sign-in link has been sent");
		verify(tokenStore, never()).save(any());
		verify(notifier, never()).sendMagicLink(any(), any());
	}

	@Test
	@DisplayName("Should return generic response without dispatching when user is suspended or deactivated")
	void shouldReturnGenericResponseWhenUserInactive() {
		User suspendedUser = new User(UserId.generate(), "suspended@clinic.org", "hash", "Suspended User", UserStatus.SUSPENDED, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("suspended@clinic.org")).thenReturn(Optional.of(suspendedUser));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("suspended@clinic.org");
		MagicLinkRequestResponse response = dispatchService.requestMagicLink(command, userRepository);

		assertThat(response.message()).contains("sign-in link has been sent");
		verify(tokenStore, never()).save(any());
		verify(notifier, never()).sendMagicLink(any(), any());

		User deactivatedUser = new User(UserId.generate(), "deactivated@clinic.org", "hash", "Deactivated User", UserStatus.DEACTIVATED, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("deactivated@clinic.org")).thenReturn(Optional.of(deactivatedUser));

		MagicLinkRequestCommand deactivatedCommand = MagicLinkRequestCommand.of("deactivated@clinic.org");
		response = dispatchService.requestMagicLink(deactivatedCommand, userRepository);

		assertThat(response.message()).contains("sign-in link has been sent");
		verify(tokenStore, never()).save(any());
		verify(notifier, never()).sendMagicLink(any(), any());
	}
}
