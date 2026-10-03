package io.github.edmaputra.iam.adapter.security.provider;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.MagicLinkAuthCredentials;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.InvalidMagicLinkException;
import io.github.edmaputra.iam.domain.exception.MagicLinkConsumedException;
import io.github.edmaputra.iam.domain.exception.MagicLinkExpiredException;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MagicLinkAuthProvider}.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
class MagicLinkAuthProviderTest {

	@Mock
	private MagicLinkTokenStorePort tokenStore;

	@Mock
	private UserRepository userRepository;

	private MagicLinkAuthProvider provider;

	@BeforeEach
	void setUp() {
		provider = new MagicLinkAuthProvider(tokenStore, userRepository);
	}

	@Test
	@DisplayName("Should support MAGIC_LINK credential type only")
	void shouldSupportOnlyMagicLink() {
		assertThat(provider.supports(AuthCredentialType.MAGIC_LINK)).isTrue();
		assertThat(provider.supports(AuthCredentialType.PASSWORD)).isFalse();
	}

	@Test
	@DisplayName("Should throw IllegalArgumentException when credentials of wrong type")
	void shouldThrowOnWrongCredentialType() {
		assertThatThrownBy(() -> provider.authenticate(new PasswordAuthCredentials("a@b.com", "pass")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should successfully authenticate valid magic link token")
	void shouldAuthenticateSuccessfully() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("valid-tok", userId, null, "user@example.com", now.plusSeconds(300), now);
		MagicLinkToken consumed = token.consume(now);
		User user = new User(userId, "user@example.com", "hash", "John Doe", UserStatus.ACTIVE, false, now, now);

		when(tokenStore.findByToken("valid-tok")).thenReturn(Optional.of(token));
		when(tokenStore.consume(eq("valid-tok"), any(Instant.class))).thenReturn(Optional.of(consumed));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		AuthenticatedIdentity identity = provider.authenticate(new MagicLinkAuthCredentials("valid-tok"));

		assertThat(identity.userId()).isEqualTo(user.getId());
		assertThat(identity.email()).isEqualTo("user@example.com");
		assertThat(identity.fullName()).isEqualTo("John Doe");
		assertThat(identity.providerType()).isEqualTo(ProviderType.MAGIC_LINK);
	}

	@Test
	@DisplayName("Should throw InvalidMagicLinkException when token not found")
	void shouldThrowWhenTokenNotFound() {
		when(tokenStore.findByToken("unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> provider.authenticate(new MagicLinkAuthCredentials("unknown")))
				.isInstanceOf(InvalidMagicLinkException.class);
	}

	@Test
	@DisplayName("Should throw MagicLinkConsumedException when token already consumed")
	void shouldThrowWhenAlreadyConsumed() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("consumed-tok", userId, null, "user@example.com", now.plusSeconds(300), now);
		MagicLinkToken consumed = token.consume(now);

		when(tokenStore.findByToken("consumed-tok")).thenReturn(Optional.of(consumed));

		assertThatThrownBy(() -> provider.authenticate(new MagicLinkAuthCredentials("consumed-tok")))
				.isInstanceOf(MagicLinkConsumedException.class);
	}

	@Test
	@DisplayName("Should throw MagicLinkExpiredException when token is expired")
	void shouldThrowWhenExpired() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("exp-tok", userId, null, "user@example.com", now.minusSeconds(10), now.minusSeconds(100));

		when(tokenStore.findByToken("exp-tok")).thenReturn(Optional.of(token));

		assertThatThrownBy(() -> provider.authenticate(new MagicLinkAuthCredentials("exp-tok")))
				.isInstanceOf(MagicLinkExpiredException.class);
	}

	@Test
	@DisplayName("Should throw AuthenticationException when user suspended or deactivated")
	void shouldThrowWhenUserSuspendedOrDeactivated() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("valid-tok", userId, null, "user@example.com", now.plusSeconds(300), now);
		MagicLinkToken consumed = token.consume(now);
		User suspendedUser = new User(userId, "user@example.com", "hash", "John Doe", UserStatus.ACTIVE, false, now, now);
		suspendedUser.suspend();

		when(tokenStore.findByToken("valid-tok")).thenReturn(Optional.of(token));
		when(tokenStore.consume(eq("valid-tok"), any(Instant.class))).thenReturn(Optional.of(consumed));
		when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

		assertThatThrownBy(() -> provider.authenticate(new MagicLinkAuthCredentials("valid-tok")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("suspended");
	}
}
