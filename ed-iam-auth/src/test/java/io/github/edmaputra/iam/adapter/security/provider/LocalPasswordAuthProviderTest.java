package io.github.edmaputra.iam.adapter.security.provider;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.auth.ApiKeyAuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link LocalPasswordAuthProvider} verifying anti-enumeration,
 * constant-time dummy verification, deferred lifecycle checks, and credential validation.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class LocalPasswordAuthProviderTest {

	private static final String DUMMY_BCRYPT_HASH = "$2a$10$e8k8dGkJbVqXg1.rM4s80edq6tEsm6R8CkWZ64x/v6r5pGvOaJ6u6";

	private UserRepository userRepository;
	private PasswordEncoderPort passwordEncoder;
	private LocalPasswordAuthProvider provider;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = mock(PasswordEncoderPort.class);
		provider = new LocalPasswordAuthProvider(userRepository, passwordEncoder);
	}

	@Test
	@DisplayName("Should support PASSWORD credential type only")
	void shouldSupportPasswordCredentialTypeOnly() {
		assertThat(provider.supports(AuthCredentialType.PASSWORD)).isTrue();
		assertThat(provider.supports(AuthCredentialType.API_KEY)).isFalse();
		assertThat(provider.supports(AuthCredentialType.MAGIC_LINK)).isFalse();
		assertThat(provider.supports(AuthCredentialType.OIDC_TOKEN)).isFalse();
		assertThat(provider.supports(null)).isFalse();
	}

	@Test
	@DisplayName("Should reject invalid credential instances with IllegalArgumentException")
	void shouldRejectInvalidCredentialInstances() {
		assertThatThrownBy(() -> provider.authenticate(new ApiKeyAuthCredentials("api-key-123")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Expected PasswordAuthCredentials");
	}

	@Test
	@DisplayName("Should execute constant-time dummy verification when user not found")
	void shouldExecuteDummyVerificationWhenUserNotFound() {
		String email = "missing@hospital.org";
		String rawPassword = "wrongPassword123!";

		when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials(email, rawPassword)))
				.isInstanceOf(AuthenticationException.class)
				.hasMessage("Invalid credentials.");

		verify(passwordEncoder).matches(rawPassword, DUMMY_BCRYPT_HASH);
	}

	@Test
	@DisplayName("Should execute constant-time dummy verification when user has no local password")
	void shouldExecuteDummyVerificationWhenUserHasNoPassword() {
		String email = "sso@hospital.org";
		String rawPassword = "anyPassword123!";
		User externalUser = User.createExternal(email, "SSO Doctor", false);

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(externalUser));

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials(email, rawPassword)))
				.isInstanceOf(AuthenticationException.class)
				.hasMessage("Invalid credentials.");

		verify(passwordEncoder).matches(rawPassword, DUMMY_BCRYPT_HASH);
	}

	@Test
	@DisplayName("Should reject with Invalid credentials when password does not match")
	void shouldRejectWhenPasswordDoesNotMatch() {
		String email = "doctor@hospital.org";
		String rawPassword = "wrongPassword";
		String storedHash = "$2a$10$validStoredHashForUserAccount1234567890123456789012345678";
		User user = User.create(email, storedHash, "Dr. House", false);

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(rawPassword, storedHash)).thenReturn(false);

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials(email, rawPassword)))
				.isInstanceOf(AuthenticationException.class)
				.hasMessage("Invalid credentials.");

		verify(passwordEncoder).matches(rawPassword, storedHash);
	}

	@Test
	@DisplayName("Should not disclose suspended status when password does not match")
	void shouldNotDiscloseSuspendedStatusOnPasswordMismatch() {
		String email = "suspended@hospital.org";
		String rawPassword = "wrongPassword";
		String storedHash = "$2a$10$storedHash";
		User user = User.create(email, storedHash, "Suspended User", false);
		user.suspend();

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(rawPassword, storedHash)).thenReturn(false);

		// Must throw generic "Invalid credentials." and NOT "User account is suspended."
		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials(email, rawPassword)))
				.isInstanceOf(AuthenticationException.class)
				.hasMessage("Invalid credentials.");
	}

	@Test
	@DisplayName("Should reject with User account is suspended only when password matches")
	void shouldRejectSuspendedAccountWhenPasswordMatches() {
		String email = "suspended@hospital.org";
		String rawPassword = "correctPassword123!";
		String storedHash = "$2a$10$storedHash";
		User user = User.create(email, storedHash, "Suspended User", false);
		user.suspend();

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(rawPassword, storedHash)).thenReturn(true);

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials(email, rawPassword)))
				.isInstanceOf(AuthenticationException.class)
				.hasMessage("User account is suspended.");
	}

	@Test
	@DisplayName("Should not disclose deactivated status when password does not match")
	void shouldNotDiscloseDeactivatedStatusOnPasswordMismatch() {
		String email = "deactivated@hospital.org";
		String rawPassword = "wrongPassword";
		String storedHash = "$2a$10$storedHash";
		User user = User.create(email, storedHash, "Deactivated User", false);
		user.deactivate();

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(rawPassword, storedHash)).thenReturn(false);

		// Must throw generic "Invalid credentials." and NOT "User account is deactivated."
		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials(email, rawPassword)))
				.isInstanceOf(AuthenticationException.class)
				.hasMessage("Invalid credentials.");
	}

	@Test
	@DisplayName("Should reject with User account is deactivated only when password matches")
	void shouldRejectDeactivatedAccountWhenPasswordMatches() {
		String email = "deactivated@hospital.org";
		String rawPassword = "correctPassword123!";
		String storedHash = "$2a$10$storedHash";
		User user = User.create(email, storedHash, "Deactivated User", false);
		user.deactivate();

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(rawPassword, storedHash)).thenReturn(true);

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials(email, rawPassword)))
				.isInstanceOf(AuthenticationException.class)
				.hasMessage("User account is deactivated.");
	}

	@Test
	@DisplayName("Should successfully authenticate valid user with correct password")
	void shouldAuthenticateSuccessfullyWhenValid() {
		String email = "doctor@hospital.org";
		String rawPassword = "correctPassword123!";
		String storedHash = "$2a$10$storedHash";
		User user = User.create(email, storedHash, "Dr. Gregory House", true);

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(rawPassword, storedHash)).thenReturn(true);

		AuthenticatedIdentity identity = provider.authenticate(new PasswordAuthCredentials(email, rawPassword));

		assertThat(identity).isNotNull();
		assertThat(identity.userId()).isEqualTo(user.getId());
		assertThat(identity.email()).isEqualTo("doctor@hospital.org");
		assertThat(identity.fullName()).isEqualTo("Dr. Gregory House");
		assertThat(identity.platformSuperAdmin()).isTrue();
		assertThat(identity.providerType()).isEqualTo(ProviderType.LOCAL);
	}
}
