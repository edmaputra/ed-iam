package io.github.edmaputra.iam.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.UserResponse;
import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;
import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserStatus;

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
	private UserController userController;

	@BeforeEach
	void setUp() {
		manageUserUseCase = mock(ManageUserUseCase.class);
		userController = new UserController(manageUserUseCase);
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
}
