package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter criteria for querying and listing users, supporting collection parameters and global search.
 *
 * @param search    optional global search term matching username, email, status, role name, or group name
 * @param usernames optional collection of username or email filters (partial match)
 * @param names     optional collection of user full name filters (partial match)
 * @param statuses  optional collection of user status filters (exact match)
 * @param roles     optional collection of role identifiers, codes, or names
 * @param groups    optional collection of group identifiers, codes, or names
 * @author edmaputra
 * @since 0.3.0
 */
public record UserFilter(
		String search,
		Set<String> usernames,
		Set<String> names,
		Set<UserStatus> statuses,
		Set<String> roles,
		Set<String> groups) {

	public UserFilter {
		search = (search != null && !search.isBlank()) ? search.trim() : null;
		usernames = sanitizeStringSet(usernames);
		names = sanitizeStringSet(names);
		statuses = (statuses != null && !statuses.isEmpty()) ? Set.copyOf(statuses) : Set.of();
		roles = sanitizeStringSet(roles);
		groups = sanitizeStringSet(groups);
	}

	/**
	 * Convenience constructor for scalar filter criteria.
	 *
	 * @param username optional username/email filter
	 * @param name     optional full name filter
	 * @param status   optional status filter
	 * @param role     optional role filter
	 * @param group    optional group filter
	 */
	public UserFilter(String username, String name, UserStatus status, String role, String group) {
		this(
				null,
				username != null ? Set.of(username) : Set.of(),
				name != null ? Set.of(name) : Set.of(),
				status != null ? Set.of(status) : Set.of(),
				role != null ? Set.of(role) : Set.of(),
				group != null ? Set.of(group) : Set.of()
		);
	}

	/**
	 * Creates a filter with only a global search term.
	 *
	 * @param search the global search term
	 * @return a {@link UserFilter} configured with the search query
	 */
	public static UserFilter ofSearch(String search) {
		return new UserFilter(search, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
	}

	/**
	 * Creates an empty {@link UserFilter} with no criteria configured.
	 *
	 * @return an empty filter
	 */
	public static UserFilter empty() {
		return new UserFilter(null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
	}

	/**
	 * Checks if this filter has no criteria configured.
	 *
	 * @return true if all criteria are empty/null
	 */
	public boolean isEmpty() {
		return search == null
				&& usernames.isEmpty()
				&& names.isEmpty()
				&& statuses.isEmpty()
				&& roles.isEmpty()
				&& groups.isEmpty();
	}

	private static Set<String> sanitizeStringSet(Set<String> input) {
		if (input == null || input.isEmpty()) {
			return Set.of();
		}
		return input.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.collect(Collectors.toUnmodifiableSet());
	}
}
