package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
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
import io.github.edmaputra.iam.application.port.out.AllowedRedirectHostResolverPort;
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
import static org.mockito.ArgumentMatchers.contains;
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
		properties = new MagicLinkProperties(true, 900L, "http://localhost:8080/", java.util.List.of("app.clinic.org"));
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
		MagicLinkProperties disabled = new MagicLinkProperties(false, 900L, "http://localhost:8080", List.of());
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

	@Test
	@DisplayName("Should accept safe relative redirect URL")
	void shouldAcceptSafeRelativeRedirect() {
		TenantId tenantId = TenantId.generate();
		User user = new User(UserId.generate(), "user@clinic.org", "hash", "Active User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("user@clinic.org")).thenReturn(Optional.of(user));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@clinic.org", tenantId, "/portal/overview");
		MagicLinkRequestResponse response = dispatchService.requestMagicLink(command, userRepository);

		assertThat(response).isNotNull();
		ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
		verify(notifier).sendMagicLink(any(), urlCaptor.capture());
		assertThat(urlCaptor.getValue()).contains("&redirect=%2Fportal%2Foverview");
	}

	@Test
	@DisplayName("Should reject untrusted external redirect URL (OWASP A10 / CWE-601)")
	void shouldRejectUntrustedExternalRedirect() {
		TenantId tenantId = TenantId.generate();
		User user = new User(UserId.generate(), "user@clinic.org", "hash", "Active User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("user@clinic.org")).thenReturn(Optional.of(user));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@clinic.org", tenantId, "https://evil-phishing.com/login");
		assertThatThrownBy(() -> dispatchService.requestMagicLink(command, userRepository))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not in the allowed redirect hosts list");
	}

	@Test
	@DisplayName("Should reject protocol-relative redirect URL (OWASP A10 / CWE-601)")
	void shouldRejectProtocolRelativeRedirect() {
		TenantId tenantId = TenantId.generate();
		User user = new User(UserId.generate(), "user@clinic.org", "hash", "Active User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("user@clinic.org")).thenReturn(Optional.of(user));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@clinic.org", tenantId, "//evil-phishing.com/login");
		assertThatThrownBy(() -> dispatchService.requestMagicLink(command, userRepository))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Malformed relative redirect URL");
	}

	@Test
	@DisplayName("Should reject dangerous scheme redirect URL")
	void shouldRejectDangerousSchemeRedirect() {
		TenantId tenantId = TenantId.generate();
		User user = new User(UserId.generate(), "user@clinic.org", "hash", "Active User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("user@clinic.org")).thenReturn(Optional.of(user));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@clinic.org", tenantId, "javascript:alert(1)");
		assertThatThrownBy(() -> dispatchService.requestMagicLink(command, userRepository))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Dangerous or unsupported redirect URL scheme");
	}

	@Test
	@DisplayName("Should use custom AllowedRedirectHostResolverPort when provided")
	void shouldUseCustomAllowedRedirectHostResolverPort() {
		AllowedRedirectHostResolverPort customResolver = (host, tenant) -> "tenant-dynamic.org".equals(host);
		MagicLinkDispatchService service = new MagicLinkDispatchService(properties, tokenStore, notifier, customResolver);

		TenantId tenantId = TenantId.generate();
		User user = new User(UserId.generate(), "user@clinic.org", "hash", "Active User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		when(userRepository.findByEmail("user@clinic.org")).thenReturn(Optional.of(user));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@clinic.org", tenantId, "https://tenant-dynamic.org/welcome");
		MagicLinkRequestResponse response = service.requestMagicLink(command, userRepository);

		assertThat(response).isNotNull();
		verify(notifier).sendMagicLink(any(), contains("tenant-dynamic.org"));
	}
}
