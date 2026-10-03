package io.github.edmaputra.iam.domain.model;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserFilter}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class UserFilterTest {

	@Test
	@DisplayName("Should create empty UserFilter and verify isEmpty")
	void shouldCreateEmptyFilter() {
		UserFilter filter = UserFilter.empty();
		assertThat(filter.isEmpty()).isTrue();
		assertThat(filter.search()).isNull();
		assertThat(filter.usernames()).isEmpty();
		assertThat(filter.names()).isEmpty();
		assertThat(filter.statuses()).isEmpty();
		assertThat(filter.roles()).isEmpty();
		assertThat(filter.groups()).isEmpty();

		UserFilter blankFilter = new UserFilter("  ", " ", null, "  ", "");
		assertThat(blankFilter.isEmpty()).isTrue();
		assertThat(blankFilter.search()).isNull();
		assertThat(blankFilter.usernames()).isEmpty();
		assertThat(blankFilter.names()).isEmpty();
		assertThat(blankFilter.roles()).isEmpty();
		assertThat(blankFilter.groups()).isEmpty();
	}

	@Test
	@DisplayName("Should create filter with global search")
	void shouldCreateSearchFilter() {
		UserFilter filter = UserFilter.ofSearch("  cardio  ");
		assertThat(filter.isEmpty()).isFalse();
		assertThat(filter.search()).isEqualTo("cardio");
		assertThat(filter.usernames()).isEmpty();
	}

	@Test
	@DisplayName("Should sanitize collections and retain valid criteria")
	void shouldRetainPopulatedCollectionsFilter() {
		UserFilter filter = new UserFilter(
				" search ",
				Set.of("  alice@test.org ", "bob@test.org  "),
				Set.of(" Alice ", " Bob "),
				Set.of(UserStatus.ACTIVE, UserStatus.SUSPENDED),
				Set.of(" ADMIN ", "DOCTOR"),
				Set.of(" CLINIC ", "RESEARCH")
		);

		assertThat(filter.isEmpty()).isFalse();
		assertThat(filter.search()).isEqualTo("search");
		assertThat(filter.usernames()).containsExactlyInAnyOrder("alice@test.org", "bob@test.org");
		assertThat(filter.names()).containsExactlyInAnyOrder("Alice", "Bob");
		assertThat(filter.statuses()).containsExactlyInAnyOrder(UserStatus.ACTIVE, UserStatus.SUSPENDED);
		assertThat(filter.roles()).containsExactlyInAnyOrder("ADMIN", "DOCTOR");
		assertThat(filter.groups()).containsExactlyInAnyOrder("CLINIC", "RESEARCH");
	}

	@Test
	@DisplayName("Should support scalar convenience constructor")
	void shouldSupportScalarConstructor() {
		UserFilter filter = new UserFilter("alice", "Alice Smith", UserStatus.ACTIVE, "ADMIN", "DOCTORS");
		assertThat(filter.isEmpty()).isFalse();
		assertThat(filter.search()).isNull();
		assertThat(filter.usernames()).containsExactly("alice");
		assertThat(filter.names()).containsExactly("Alice Smith");
		assertThat(filter.statuses()).containsExactly(UserStatus.ACTIVE);
		assertThat(filter.roles()).containsExactly("ADMIN");
		assertThat(filter.groups()).containsExactly("DOCTORS");
	}
}
