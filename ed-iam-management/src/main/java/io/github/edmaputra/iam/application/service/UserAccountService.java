package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

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
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

/**
 * Focused application service responsible for user account lifecycle, profile management,
 * credentials, and session invalidation.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class UserAccountService {

	private final UserRepository userRepository;
	private final PasswordEncoderPort passwordEncoder;
	private final SessionRegistryPort sessionRegistry;
	private final TokenRevocationPort tokenRevocationPort;
	private final UserMfaRepository userMfaRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public UserAccountService(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			UserMfaRepository userMfaRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoderPort must not be null.");
		this.sessionRegistry = sessionRegistry;
		this.tokenRevocationPort = tokenRevocationPort;
		this.userMfaRepository = userMfaRepository;
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public UserAccountService(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder) {
		this(userRepository, passwordEncoder, null, null, null, null, null);
	}

	@Transactional
	public User createUser(CreateUserCommand command) {
		Objects.requireNonNull(command, "CreateUserCommand must not be null.");

		if (command.platformSuperAdmin() && currentActorProvider != null) {
			currentActorProvider.currentActor().ifPresent(actor -> {
				if (!actor.isPlatformSuperAdmin()) {
					throw new AccessDeniedException("Only platform superadmins can create platform superadmin accounts.");
				}
			});
		}

		if (userRepository.findByEmail(command.email()).isPresent()) {
			throw new IllegalArgumentException("User with email already exists: " + command.email());
		}

		String passwordHash = (command.password() != null && !command.password().isBlank())
				? passwordEncoder.encode(command.password())
				: null;

		User user = User.create(
				command.email(),
				passwordHash,
				command.fullName(),
				command.platformSuperAdmin());

		User saved = userRepository.save(user);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_CREATED,
					null,
					saved.getId().value(),
					"USER",
					Map.of("email", saved.getEmail(), "fullName", saved.getFullName()),
					actorStr));
		}
		return saved;
	}

	@Transactional(readOnly = true)
	public User getUserById(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		return userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id.value()));
	}

	@Transactional(readOnly = true)
	public User getUserByEmail(String email) {
		Objects.requireNonNull(email, "Email must not be null.");
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
	}

	@Transactional
	public User updateUser(UpdateUserCommand command) {
		Objects.requireNonNull(command, "UpdateUserCommand must not be null.");
		User user = getUserById(command.userId());
		user.updateProfile(command.fullName());
		User saved = userRepository.save(user);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_UPDATED,
					null,
					saved.getId().value(),
					"USER",
					Map.of("email", saved.getEmail(), "fullName", saved.getFullName()),
					actorStr));
		}
		return saved;
	}

	@Transactional
	public User changeUserStatus(ChangeUserStatusCommand command) {
		Objects.requireNonNull(command, "ChangeUserStatusCommand must not be null.");
		User user = getUserById(command.userId());

		switch (command.status()) {
			case ACTIVE -> user.activate();
			case SUSPENDED -> {
				user.suspend();
				revokeUserSessions(user.getId());
			}
			case DEACTIVATED -> {
				user.deactivate();
				revokeUserSessions(user.getId());
			}
		}

		User saved = userRepository.save(user);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_STATUS_CHANGED,
					null,
					saved.getId().value(),
					"USER",
					Map.of("email", saved.getEmail(), "status", saved.getStatus().name()),
					actorStr));
			if (saved.isDeactivated()) {
				eventPublisher.publish(IamEvent.of(
						IamEventTypes.USER_DEACTIVATED,
						null,
						saved.getId().value(),
						"USER",
						Map.of("email", saved.getEmail()),
						actorStr));
			}
		}
		return saved;
	}

	@Transactional
	public void deleteUser(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		User user = userRepository.findById(id).orElse(null);
		revokeUserSessions(id);
		if (userMfaRepository != null) {
			userMfaRepository.deleteByUserId(id);
		}
		userRepository.delete(id);
		if (eventPublisher != null && user != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_DEACTIVATED,
					null,
					id.value(),
					"USER",
					Map.of("email", user.getEmail()),
					actorStr));
		}
	}

	@Transactional(readOnly = true)
	public PagedResult<User> getUsers(UserFilter filter, PageQuery pageQuery) {
		UserFilter resolvedFilter = filter != null ? filter : UserFilter.empty();
		return userRepository.findAll(resolvedFilter, pageQuery);
	}

	private void revokeUserSessions(UserId userId) {
		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeAllForUser(userId, Instant.now());
		}
		if (sessionRegistry != null) {
			java.util.List<UserSession> activeSessions = sessionRegistry.findActiveSessions(userId, null);
			for (UserSession s : activeSessions) {
				sessionRegistry.revokeSession(s.id());
				if (tokenRevocationPort != null && s.tokenIdentifier() != null) {
					tokenRevocationPort.revokeToken(s.tokenIdentifier(), s.expiresAt());
				}
			}
		}
	}

	private String resolveActor() {
		return currentActorProvider != null && currentActorProvider.currentActor().isPresent() &&
				currentActorProvider.currentActor().get().userId() != null
				? currentActorProvider.currentActor().get().userId().toString()
				: "system";
	}
}
