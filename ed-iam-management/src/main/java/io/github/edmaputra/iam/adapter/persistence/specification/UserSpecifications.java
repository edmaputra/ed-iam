package io.github.edmaputra.iam.adapter.persistence.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import io.github.edmaputra.iam.adapter.persistence.entity.GroupJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.RoleJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserGroupMembershipJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserRoleAssignmentJpaEntity;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserStatus;

/**
 * JPA dynamic query specifications for filtering {@link UserJpaEntity} records,
 * supporting collection filters and cross-field global LIKE searches.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public final class UserSpecifications {

	private UserSpecifications() {}

	/**
	 * Builds a dynamic {@link Specification} based on the supplied {@link UserFilter}.
	 *
	 * @param filter the user filter criteria
	 * @return JPA specification
	 */
	public static Specification<UserJpaEntity> withFilter(UserFilter filter) {
		return (root, query, cb) -> {
			if (filter == null || filter.isEmpty()) {
				return cb.conjunction();
			}

			List<Predicate> andPredicates = new ArrayList<>();

			// 1. Global search across username, email, status, role name, and group name using LIKE
			if (filter.search() != null) {
				String searchPattern = "%" + filter.search().toLowerCase() + "%";
				List<Predicate> searchOrPredicates = new ArrayList<>();

				// Email / Username
				searchOrPredicates.add(cb.like(cb.lower(root.get("email")), searchPattern));

				// Full Name
				searchOrPredicates.add(cb.like(cb.lower(root.get("fullName")), searchPattern));

				// Status
				searchOrPredicates.add(cb.like(cb.lower(root.get("status")), searchPattern));

				// Role Name / Code via subquery
				Subquery<Integer> searchRoleSubquery = query.subquery(Integer.class);
				Root<UserRoleAssignmentJpaEntity> sRoleAssignment = searchRoleSubquery.from(UserRoleAssignmentJpaEntity.class);
				Root<RoleJpaEntity> sRole = searchRoleSubquery.from(RoleJpaEntity.class);
				searchRoleSubquery.select(cb.literal(1));
				searchRoleSubquery.where(cb.and(
						cb.equal(sRoleAssignment.get("roleId"), sRole.get("id")),
						cb.equal(sRoleAssignment.get("userId"), root.get("id")),
						cb.or(
								cb.like(cb.lower(sRole.get("name")), searchPattern),
								cb.like(cb.lower(sRole.get("code")), searchPattern)
						)
				));
				searchOrPredicates.add(cb.exists(searchRoleSubquery));

				// Group Name / Code via subquery
				Subquery<Integer> searchGroupSubquery = query.subquery(Integer.class);
				Root<UserGroupMembershipJpaEntity> sMembership = searchGroupSubquery.from(UserGroupMembershipJpaEntity.class);
				Root<GroupJpaEntity> sGroup = searchGroupSubquery.from(GroupJpaEntity.class);
				searchGroupSubquery.select(cb.literal(1));
				searchGroupSubquery.where(cb.and(
						cb.equal(sMembership.get("id").get("groupId"), sGroup.get("id")),
						cb.equal(sMembership.get("id").get("userId"), root.get("id")),
						cb.or(
								cb.like(cb.lower(sGroup.get("name")), searchPattern),
								cb.like(cb.lower(sGroup.get("code")), searchPattern)
						)
				));
				searchOrPredicates.add(cb.exists(searchGroupSubquery));

				andPredicates.add(cb.or(searchOrPredicates.toArray(new Predicate[0])));
			}

			// 2. Collection filter: Usernames / Emails
			if (!filter.usernames().isEmpty()) {
				List<Predicate> usernameOrPredicates = new ArrayList<>();
				for (String username : filter.usernames()) {
					usernameOrPredicates.add(cb.like(cb.lower(root.get("email")), "%" + username.toLowerCase() + "%"));
				}
				andPredicates.add(cb.or(usernameOrPredicates.toArray(new Predicate[0])));
			}

			// 3. Collection filter: Names
			if (!filter.names().isEmpty()) {
				List<Predicate> nameOrPredicates = new ArrayList<>();
				for (String name : filter.names()) {
					nameOrPredicates.add(cb.like(cb.lower(root.get("fullName")), "%" + name.toLowerCase() + "%"));
				}
				andPredicates.add(cb.or(nameOrPredicates.toArray(new Predicate[0])));
			}

			// 4. Collection filter: Statuses
			if (!filter.statuses().isEmpty()) {
				List<String> statusNames = filter.statuses().stream().map(UserStatus::name).toList();
				andPredicates.add(root.get("status").in(statusNames));
			}

			// 5. Collection filter: Roles (matches any in collection)
			if (!filter.roles().isEmpty()) {
				Subquery<Integer> roleSubquery = query.subquery(Integer.class);
				Root<UserRoleAssignmentJpaEntity> assignment = roleSubquery.from(UserRoleAssignmentJpaEntity.class);
				Root<RoleJpaEntity> role = roleSubquery.from(RoleJpaEntity.class);
				roleSubquery.select(cb.literal(1));

				Predicate roleJoin = cb.equal(assignment.get("roleId"), role.get("id"));
				Predicate userCorrelate = cb.equal(assignment.get("userId"), root.get("id"));

				List<Predicate> roleMatches = new ArrayList<>();
				for (String r : filter.roles()) {
					UUID roleUuid = tryParseUuid(r);
					if (roleUuid != null) {
						roleMatches.add(cb.equal(role.get("id"), roleUuid));
					}
					roleMatches.add(cb.equal(cb.lower(role.get("code")), r.toLowerCase()));
					roleMatches.add(cb.like(cb.lower(role.get("name")), "%" + r.toLowerCase() + "%"));
				}

				roleSubquery.where(cb.and(roleJoin, userCorrelate, cb.or(roleMatches.toArray(new Predicate[0]))));
				andPredicates.add(cb.exists(roleSubquery));
			}

			// 6. Collection filter: Groups (matches any in collection)
			if (!filter.groups().isEmpty()) {
				Subquery<Integer> groupSubquery = query.subquery(Integer.class);
				Root<UserGroupMembershipJpaEntity> membership = groupSubquery.from(UserGroupMembershipJpaEntity.class);
				Root<GroupJpaEntity> group = groupSubquery.from(GroupJpaEntity.class);
				groupSubquery.select(cb.literal(1));

				Predicate groupJoin = cb.equal(membership.get("id").get("groupId"), group.get("id"));
				Predicate userCorrelate = cb.equal(membership.get("id").get("userId"), root.get("id"));

				List<Predicate> groupMatches = new ArrayList<>();
				for (String g : filter.groups()) {
					UUID groupUuid = tryParseUuid(g);
					if (groupUuid != null) {
						groupMatches.add(cb.equal(group.get("id"), groupUuid));
					}
					groupMatches.add(cb.equal(cb.lower(group.get("code")), g.toLowerCase()));
					groupMatches.add(cb.like(cb.lower(group.get("name")), "%" + g.toLowerCase() + "%"));
				}

				groupSubquery.where(cb.and(groupJoin, userCorrelate, cb.or(groupMatches.toArray(new Predicate[0]))));
				andPredicates.add(cb.exists(groupSubquery));
			}

			return cb.and(andPredicates.toArray(new Predicate[0]));
		};
	}

	private static UUID tryParseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value.trim());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
