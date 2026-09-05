package io.github.edmaputra.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying User entity lifecycle, statuses, and validation invariants.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class UserTest {

	@Test
	@DisplayName("Should successfully create active user with password")
	void shouldCreateActiveUser() {
		User user = User.create("doctor.alice@hospital.org", "hashedPassword123", "Dr. Alice Smith", false);

		assertThat(user.getId()).isNotNull();
		assertThat(user.getEmail()).isEqualTo("doctor.alice@hospital.org");
		assertThat(user.getPasswordHash()).isEqualTo("hashedPassword123");
		assertThat(user.getFullName()).isEqualTo("Dr. Alice Smith");
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.isPlatformSuperAdmin()).isFalse();
		assertThat(user.isActive()).isTrue();
		assertThat(user.isSuspended()).isFalse();
		assertThat(user.isDeactivated()).isFalse();
		assertThat(user.optionalPasswordHash()).contains("hashedPassword123");
	}

	@Test
	@DisplayName("Should create external SSO user without password")
	void shouldCreateExternalUser() {
		User user = User.createExternal("sso.user@hospital.org", "SSO User", false);

		assertThat(user.getPasswordHash()).isNull();
		assertThat(user.optionalPasswordHash()).isEmpty();
		assertThat(user.isActive()).isTrue();
	}

	@Test
	@DisplayName("Should reject invalid email")
	void shouldRejectInvalidEmail() {
		assertThatThrownBy(() -> User.create(null, "pwd", "Name", false))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> User.create("invalid-email", "pwd", "Name", false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid email format");
	}

	@Test
	@DisplayName("Should reject invalid full name")
	void shouldRejectInvalidFullName() {
		assertThatThrownBy(() -> User.create("valid@email.com", "pwd", null, false))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> User.create("valid@email.com", "pwd", "   ", false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Full name must not be blank");
	}

	@Test
	@DisplayName("Should update user profile and password")
	void shouldUpdateProfileAndPassword() {
		User user = User.create("alice@hospital.org", "oldPwd", "Alice A.", false);

		user.updateProfile("Alice Smith, M.D.");
		assertThat(user.getFullName()).isEqualTo("Alice Smith, M.D.");

		user.updatePassword("newHashedPwd");
		assertThat(user.getPasswordHash()).isEqualTo("newHashedPwd");
	}

	@Test
	@DisplayName("Should handle status lifecycle transitions")
	void shouldHandleStatusTransitions() {
		User user = User.create("bob@hospital.org", "pwd", "Bob", false);

		user.suspend();
		assertThat(user.isSuspended()).isTrue();
		assertThat(user.isActive()).isFalse();

		user.activate();
		assertThat(user.isActive()).isTrue();
		assertThat(user.isSuspended()).isFalse();

		user.deactivate();
		assertThat(user.isDeactivated()).isTrue();

		// Cannot activate or suspend deactivated user
		assertThatThrownBy(user::activate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Deactivated user cannot be directly activated");

		assertThatThrownBy(user::suspend)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Deactivated user cannot be suspended");
	}
}
