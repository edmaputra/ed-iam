package io.github.edmaputra.iam.adapter.security.provider;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class LocalPasswordAuthProviderTest {

	private UserRepository userRepository;
	private PasswordEncoderPort passwordEncoder;
	private LocalPasswordAuthProvider provider;

	@BeforeEach
	void setUp() {
		userRepository = Mockito.mock(UserRepository.class);
		passwordEncoder = Mockito.mock(PasswordEncoderPort.class);
		provider = new LocalPasswordAuthProvider(userRepository, passwordEncoder);
	}

	@Test
	@DisplayName("Should support PASSWORD credential type")
	void shouldSupportPasswordType() {
		assertThat(provider.supports(AuthCredentialType.PASSWORD)).isTrue();
		assertThat(provider.supports(AuthCredentialType.OIDC_TOKEN)).isFalse();
	}

	@Test
	@DisplayName("Should authenticate active user with valid password")
	void shouldAuthenticateActiveUser() {
		User user = User.create("doctor@hospital.org", "hashed-secret", "Dr. Doctor", false);

		when(userRepository.findByEmail("doctor@hospital.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret123", "hashed-secret")).thenReturn(true);

		AuthenticatedIdentity identity = provider.authenticate(
				new PasswordAuthCredentials("doctor@hospital.org", "secret123"));

		assertThat(identity).isNotNull();
		assertThat(identity.userId()).isEqualTo(user.getId());
		assertThat(identity.email()).isEqualTo("doctor@hospital.org");
		assertThat(identity.fullName()).isEqualTo("Dr. Doctor");
		assertThat(identity.platformSuperAdmin()).isFalse();
		assertThat(identity.providerType()).isEqualTo(ProviderType.LOCAL);
	}

	@Test
	@DisplayName("Should reject authentication for non-existent user")
	void shouldRejectNonExistentUser() {
		when(userRepository.findByEmail("unknown@hospital.org")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials("unknown@hospital.org", "pass")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Invalid credentials");
	}

	@Test
	@DisplayName("Should reject authentication when password does not match")
	void shouldRejectWrongPassword() {
		User user = User.create("doctor@hospital.org", "hashed-secret", "Dr. Doctor", false);

		when(userRepository.findByEmail("doctor@hospital.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-pass", "hashed-secret")).thenReturn(false);

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials("doctor@hospital.org", "wrong-pass")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Invalid credentials");
	}

	@Test
	@DisplayName("Should reject authentication for suspended user")
	void shouldRejectSuspendedUser() {
		User user = User.create("doctor@hospital.org", "hashed-secret", "Dr. Doctor", false);
		user.suspend();

		when(userRepository.findByEmail("doctor@hospital.org")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials("doctor@hospital.org", "secret123")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("suspended");
	}

	@Test
	@DisplayName("Should reject authentication for deactivated user")
	void shouldRejectDeactivatedUser() {
		User user = User.create("doctor@hospital.org", "hashed-secret", "Dr. Doctor", false);
		user.deactivate();

		when(userRepository.findByEmail("doctor@hospital.org")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials("doctor@hospital.org", "secret123")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("deactivated");
	}

	@Test
	@DisplayName("Should reject authentication when account has no password hash (SSO only)")
	void shouldRejectUserWithoutPassword() {
		User user = User.createExternal("sso-only@hospital.org", "SSO User", false);

		when(userRepository.findByEmail("sso-only@hospital.org")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials("sso-only@hospital.org", "secret123")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("not configured");
	}
}
