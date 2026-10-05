package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.in.UserCommands.ChangeUserStatusCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.CreateUserCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.UpdateUserCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link UserAccountService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class UserAccountServiceTest {

	private UserRepository userRepository;
	private PasswordEncoderPort passwordEncoder;
	private SessionRegistryPort sessionRegistry;
	private TokenRevocationPort tokenRevocationPort;
	private UserMfaRepository userMfaRepository;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;

	private UserAccountService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = mock(PasswordEncoderPort.class);
		sessionRegistry = mock(SessionRegistryPort.class);
		tokenRevocationPort = mock(TokenRevocationPort.class);
		userMfaRepository = mock(UserMfaRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);

		service = new UserAccountService(
				userRepository,
				passwordEncoder,
				sessionRegistry,
				tokenRevocationPort,
				userMfaRepository,
				currentActorProvider,
				eventPublisher);
	}

	@Test
	@DisplayName("Should create user with password and publish event")
	void shouldCreateUser() {
		when(userRepository.findByEmail("alice@test.org")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("secret")).thenReturn("hashed");
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		CreateUserCommand command = new CreateUserCommand("alice@test.org", "secret", "Alice", false);
		User user = service.createUser(command);

		assertThat(user.getEmail()).isEqualTo("alice@test.org");
		assertThat(user.getPasswordHash()).isEqualTo("hashed");
		verify(userRepository).save(any(User.class));

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.USER_CREATED);
	}

	@Test
	@DisplayName("Should reject superadmin creation if caller is not platform superadmin")
	void shouldRejectSuperAdminCreationWhenNotSuperAdmin() {
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		CreateUserCommand command = new CreateUserCommand("root@test.org", "secret", "Root", true);

		assertThatThrownBy(() -> service.createUser(command))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Only platform superadmins can create platform superadmin accounts.");
	}

	@Test
	@DisplayName("Should reject user creation when email already exists")
	void shouldRejectDuplicateEmail() {
		User existing = User.create("existing@test.org", "hash", "Existing", false);
		when(userRepository.findByEmail("existing@test.org")).thenReturn(Optional.of(existing));

		CreateUserCommand command = new CreateUserCommand("existing@test.org", "pass", "Name", false);

		assertThatThrownBy(() -> service.createUser(command))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("User with email already exists: existing@test.org");
	}

	@Test
	@DisplayName("Should find user by id or throw UserNotFoundException")
	void shouldFindUserById() {
		UserId userId = UserId.generate();
		User user = User.create("alice@test.org", "hash", "Alice", false);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		assertThat(service.getUserById(userId)).isEqualTo(user);

		UserId unknownId = UserId.generate();
		when(userRepository.findById(unknownId)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getUserById(unknownId))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should find user by email or throw UserNotFoundException")
	void shouldFindUserByEmail() {
		User user = User.create("alice@test.org", "hash", "Alice", false);
		when(userRepository.findByEmail("alice@test.org")).thenReturn(Optional.of(user));

		assertThat(service.getUserByEmail("alice@test.org")).isEqualTo(user);

		when(userRepository.findByEmail("bob@test.org")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getUserByEmail("bob@test.org"))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should update user profile and publish event")
	void shouldUpdateUser() {
		UserId userId = UserId.generate();
		User user = User.create("alice@test.org", "hash", "Alice", false);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		User updated = service.updateUser(new UpdateUserCommand(userId, "Alice Smith"));
		assertThat(updated.getFullName()).isEqualTo("Alice Smith");

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.USER_UPDATED);
	}

	@Test
	@DisplayName("Should change user status and revoke active sessions on suspend/deactivate")
	void shouldChangeUserStatus() {
		User user = User.create("alice@test.org", "hash", "Alice", false);
		UserId userId = user.getId();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		io.github.edmaputra.iam.domain.model.SessionId sessionId = io.github.edmaputra.iam.domain.model.SessionId.generate();
		UserSession session = mock(UserSession.class);
		when(session.id()).thenReturn(sessionId);
		when(session.tokenIdentifier()).thenReturn("token-123");
		when(session.expiresAt()).thenReturn(java.time.Instant.now().plusSeconds(3600));
		when(sessionRegistry.findActiveSessions(userId, null)).thenReturn(List.of(session));

		User suspended = service.changeUserStatus(new ChangeUserStatusCommand(userId, UserStatus.SUSPENDED));
		assertThat(suspended.getStatus()).isEqualTo(UserStatus.SUSPENDED);
		verify(tokenRevocationPort).revokeAllForUser(any(), any());
		verify(sessionRegistry).revokeSession(sessionId);

		User deactivated = service.changeUserStatus(new ChangeUserStatusCommand(userId, UserStatus.DEACTIVATED));
		assertThat(deactivated.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
	}

	@Test
	@DisplayName("Should delete user, revoke sessions, and remove MFA")
	void shouldDeleteUser() {
		UserId userId = UserId.generate();
		User user = User.create("alice@test.org", "hash", "Alice", false);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		service.deleteUser(userId);

		verify(tokenRevocationPort).revokeAllForUser(any(), any());
		verify(userMfaRepository).deleteByUserId(userId);
		verify(userRepository).delete(userId);
		verify(eventPublisher).publish(any(IamEvent.class));
	}

	@Test
	@DisplayName("Should query users with filter and pagination")
	void shouldGetUsers() {
		PageQuery query = PageQuery.of(0, 10);
		PagedResult<User> expected = new PagedResult<>(List.of(), 0, 10, 0L, 0);
		when(userRepository.findAll(UserFilter.empty(), query)).thenReturn(expected);

		PagedResult<User> actual = service.getUsers(null, query);
		assertThat(actual).isSameAs(expected);
	}
}
