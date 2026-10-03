package io.github.edmaputra.iam.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.SessionManagementDtos.UserLockoutResponse;
import io.github.edmaputra.iam.adapter.rest.dto.SessionManagementDtos.UserSessionDetailResponse;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.UserResponse;
import io.github.edmaputra.iam.application.port.in.ManageLockoutUseCase;
import io.github.edmaputra.iam.application.port.in.ManageSessionUseCase;
import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserController}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class UserControllerTest {

	private ManageUserUseCase manageUserUseCase;
	private ManageSessionUseCase manageSessionUseCase;
	private ManageLockoutUseCase manageLockoutUseCase;
	private UserController userController;

	@BeforeEach
	void setUp() {
		manageUserUseCase = mock(ManageUserUseCase.class);
		manageSessionUseCase = mock(ManageSessionUseCase.class);
		manageLockoutUseCase = mock(ManageLockoutUseCase.class);

		org.springframework.beans.factory.ObjectProvider<ManageSessionUseCase> sessionProvider =
				mock(org.springframework.beans.factory.ObjectProvider.class);
		when(sessionProvider.getIfAvailable()).thenReturn(manageSessionUseCase);

		org.springframework.beans.factory.ObjectProvider<ManageLockoutUseCase> lockoutProvider =
				mock(org.springframework.beans.factory.ObjectProvider.class);
		when(lockoutProvider.getIfAvailable()).thenReturn(manageLockoutUseCase);

		userController = new UserController(manageUserUseCase, sessionProvider, lockoutProvider);
	}

	@Test
	@DisplayName("Should return paginated users with mapped UserResponse")
	void shouldGetUsersPaginated() {
		User user1 = User.create("alice@test.org", "hash", "Alice", false);
		User user2 = User.create("bob@test.org", "hash", "Bob", true);
		PagedResult<User> domainPage = new PagedResult<>(List.of(user1, user2), 0, 20, 2L, 1);

		when(manageUserUseCase.getUsers(any(UserFilter.class), any(PageQuery.class))).thenReturn(domainPage);

		ResponseEntity<PagedResult<UserResponse>> response = userController.getUsers(null, null, null, null, null, null, 0, 20);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().content()).hasSize(2);
		assertThat(response.getBody().content().get(0).email()).isEqualTo("alice@test.org");
		assertThat(response.getBody().content().get(1).email()).isEqualTo("bob@test.org");
		assertThat(response.getBody().totalElements()).isEqualTo(2L);
		assertThat(response.getBody().totalPages()).isEqualTo(1);
		assertThat(response.getBody().page()).isZero();
		assertThat(response.getBody().size()).isEqualTo(20);

		verify(manageUserUseCase).getUsers(UserFilter.empty(), PageQuery.of(0, 20));
	}

	@Test
	@DisplayName("Should pass filter criteria to use case")
	void shouldGetUsersWithFilter() {
		User user = User.create("alice@test.org", "hash", "Alice", false);
		PagedResult<User> domainPage = new PagedResult<>(List.of(user), 0, 10, 1L, 1);

		when(manageUserUseCase.getUsers(any(UserFilter.class), any(PageQuery.class))).thenReturn(domainPage);

		ResponseEntity<PagedResult<UserResponse>> response = userController.getUsers(
				"cardio", java.util.Set.of("alice"), java.util.Set.of("Alice"), java.util.Set.of(UserStatus.ACTIVE), java.util.Set.of("ADMIN"), java.util.Set.of("DOCTORS"), 0, 10);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().content()).hasSize(1);

		UserFilter expectedFilter = new UserFilter("cardio", java.util.Set.of("alice"), java.util.Set.of("Alice"), java.util.Set.of(UserStatus.ACTIVE), java.util.Set.of("ADMIN"), java.util.Set.of("DOCTORS"));
		verify(manageUserUseCase).getUsers(expectedFilter, PageQuery.of(0, 10));
	}

	@Test
	@DisplayName("Should get user by email")
	void shouldGetUserByEmail() {
		User user = User.create("alice@test.org", "hash", "Alice", false);
		when(manageUserUseCase.getUserByEmail("alice@test.org")).thenReturn(user);

		ResponseEntity<UserResponse> response = userController.getUserByEmail("alice@test.org");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().email()).isEqualTo("alice@test.org");
		verify(manageUserUseCase).getUserByEmail("alice@test.org");
	}

	@Test
	@DisplayName("Should get user by id")
	void shouldGetUserById() {
		UUID uuid = UUID.randomUUID();
		User user = User.create("alice@test.org", "hash", "Alice", false);
		when(manageUserUseCase.getUserById(new UserId(uuid))).thenReturn(user);

		ResponseEntity<UserResponse> response = userController.getUserById(uuid);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().email()).isEqualTo("alice@test.org");
		verify(manageUserUseCase).getUserById(new UserId(uuid));
	}

	@Test
	@DisplayName("Should get user sessions")
	void shouldGetUserSessions() {
		UUID uuid = UUID.randomUUID();
		UUID tenantUuid = UUID.randomUUID();
		UserSession s1 = UserSession.create(new UserId(uuid), new TenantId(tenantUuid),
				"tok-1", 3600, "127.0.0.1", "Chrome");

		when(manageSessionUseCase.listUserSessions(new UserId(uuid), new TenantId(tenantUuid)))
				.thenReturn(List.of(s1));

		ResponseEntity<List<UserSessionDetailResponse>> response = userController.getUserSessions(uuid, tenantUuid.toString());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).hasSize(1);
		assertThat(response.getBody().getFirst().ipAddress()).isEqualTo("127.0.0.1");
	}

	@Test
	@DisplayName("Should terminate specific session")
	void shouldTerminateSession() {
		UUID uuid = UUID.randomUUID();
		UUID sessionUuid = UUID.randomUUID();

		ResponseEntity<Void> response = userController.terminateSession(uuid, sessionUuid);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(manageSessionUseCase).terminateSession(new UserId(uuid), new SessionId(sessionUuid));
	}

	@Test
	@DisplayName("Should terminate all sessions for user")
	void shouldTerminateAllSessions() {
		UUID uuid = UUID.randomUUID();
		UUID tenantUuid = UUID.randomUUID();

		ResponseEntity<Void> response = userController.terminateAllSessions(uuid, tenantUuid.toString());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(manageSessionUseCase).terminateAllUserSessions(new UserId(uuid), new TenantId(tenantUuid));
	}

	@Test
	@DisplayName("Should get lockout status")
	void shouldGetLockoutStatus() {
		UUID uuid = UUID.randomUUID();
		when(manageLockoutUseCase.getLockoutStatus(new UserId(uuid)))
				.thenReturn(LockoutStatus.unlocked(2));

		ResponseEntity<UserLockoutResponse> response = userController.getLockoutStatus(uuid);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().locked()).isFalse();
		assertThat(response.getBody().failedAttempts()).isEqualTo(2);
	}

	@Test
	@DisplayName("Should unlock user")
	void shouldUnlockUser() {
		UUID uuid = UUID.randomUUID();

		ResponseEntity<Void> response = userController.unlockUser(uuid);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(manageLockoutUseCase).unlockUser(new UserId(uuid));
	}
}
