package io.github.edmaputra.iam.adapter.persistence.specification;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import io.github.edmaputra.iam.adapter.persistence.entity.GroupJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.RoleJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserGroupMembershipJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserRoleAssignmentJpaEntity;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserSpecifications}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class UserSpecificationsTest {

	private Root<UserJpaEntity> root;
	private CriteriaQuery<?> query;
	private CriteriaBuilder cb;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		root = mock(Root.class);
		query = mock(CriteriaQuery.class);
		cb = mock(CriteriaBuilder.class);
	}

	@Test
	@DisplayName("Should return conjunction predicate when filter is null or empty")
	void shouldReturnConjunctionWhenEmpty() {
		Predicate conjunction = mock(Predicate.class);
		when(cb.conjunction()).thenReturn(conjunction);

		Specification<UserJpaEntity> specNull = UserSpecifications.withFilter(null);
		Predicate resultNull = specNull.toPredicate(root, query, cb);
		assertThat(resultNull).isSameAs(conjunction);

		Specification<UserJpaEntity> specEmpty = UserSpecifications.withFilter(UserFilter.empty());
		Predicate resultEmpty = specEmpty.toPredicate(root, query, cb);
		assertThat(resultEmpty).isSameAs(conjunction);
	}

	@Test
	@DisplayName("Should build predicates for username, name, and status")
	@SuppressWarnings("unchecked")
	void shouldBuildFieldPredicates() {
		Path<String> emailPath = mock(Path.class);
		Path<String> namePath = mock(Path.class);
		Path<String> statusPath = mock(Path.class);
		Expression<String> lowerEmail = mock(Expression.class);
		Expression<String> lowerName = mock(Expression.class);
		Predicate p1 = mock(Predicate.class);
		Predicate p2 = mock(Predicate.class);
		Predicate p3 = mock(Predicate.class);
		Predicate or1 = mock(Predicate.class);
		Predicate or2 = mock(Predicate.class);
		Predicate combined = mock(Predicate.class);

		when(root.<String>get("email")).thenReturn(emailPath);
		when(root.<String>get("fullName")).thenReturn(namePath);
		when(root.<String>get("status")).thenReturn(statusPath);

		when(cb.lower(emailPath)).thenReturn(lowerEmail);
		when(cb.lower(namePath)).thenReturn(lowerName);

		when(cb.like(lowerEmail, "%alice%")).thenReturn(p1);
		when(cb.like(lowerName, "%alice smith%")).thenReturn(p2);
		when(statusPath.in(any(Collection.class))).thenReturn(p3);

		when(cb.or(any(Predicate[].class))).thenReturn(or1, or2);
		when(cb.and(any(Predicate[].class))).thenReturn(combined);

		UserFilter filter = new UserFilter("alice", "Alice Smith", UserStatus.ACTIVE, null, null);
		Specification<UserJpaEntity> spec = UserSpecifications.withFilter(filter);
		Predicate result = spec.toPredicate(root, query, cb);

		assertThat(result).isSameAs(combined);
		verify(cb).like(lowerEmail, "%alice%");
		verify(cb).like(lowerName, "%alice smith%");
		verify(statusPath).in(any(Collection.class));
	}

	@Test
	@DisplayName("Should build global search predicates matching multiple fields")
	@SuppressWarnings("unchecked")
	void shouldBuildGlobalSearchPredicate() {
		Path<String> emailPath = mock(Path.class);
		Path<String> namePath = mock(Path.class);
		Path<String> statusPath = mock(Path.class);
		Expression<String> lowerEmail = mock(Expression.class);
		Expression<String> lowerName = mock(Expression.class);
		Expression<String> lowerStatus = mock(Expression.class);

		when(root.<String>get("email")).thenReturn(emailPath);
		when(root.<String>get("fullName")).thenReturn(namePath);
		when(root.<String>get("status")).thenReturn(statusPath);

		when(cb.lower(emailPath)).thenReturn(lowerEmail);
		when(cb.lower(namePath)).thenReturn(lowerName);
		when(cb.lower(statusPath)).thenReturn(lowerStatus);

		Subquery<Integer> roleSubquery = mock(Subquery.class);
		Subquery<Integer> groupSubquery = mock(Subquery.class);
		when(query.subquery(Integer.class)).thenReturn(roleSubquery, groupSubquery);

		Root<UserRoleAssignmentJpaEntity> rAssign = mock(Root.class);
		Root<RoleJpaEntity> rRole = mock(Root.class);
		when(roleSubquery.from(UserRoleAssignmentJpaEntity.class)).thenReturn(rAssign);
		when(roleSubquery.from(RoleJpaEntity.class)).thenReturn(rRole);

		Root<UserGroupMembershipJpaEntity> gMember = mock(Root.class);
		Root<GroupJpaEntity> gGroup = mock(Root.class);
		when(groupSubquery.from(UserGroupMembershipJpaEntity.class)).thenReturn(gMember);
		when(groupSubquery.from(GroupJpaEntity.class)).thenReturn(gGroup);

		Path<Object> roleAssignId = mock(Path.class);
		Path<Object> roleId = mock(Path.class);
		when(rAssign.get("roleId")).thenReturn(roleAssignId);
		when(rRole.get("id")).thenReturn(roleId);

		Path<Object> roleAssignUserId = mock(Path.class);
		Path<Object> rootId = mock(Path.class);
		when(rAssign.get("userId")).thenReturn(roleAssignUserId);
		when(root.get("id")).thenReturn(rootId);

		Path<String> roleName = mock(Path.class);
		Path<String> roleCode = mock(Path.class);
		when(rRole.<String>get("name")).thenReturn(roleName);
		when(rRole.<String>get("code")).thenReturn(roleCode);

		Path<Object> gMemberGroupId = mock(Path.class);
		Path<Object> gMemberEmbedId = mock(Path.class);
		when(rAssign.get("roleId")).thenReturn(roleAssignId);
		when(gMember.get("id")).thenReturn(gMemberEmbedId);
		when(gMemberEmbedId.get("groupId")).thenReturn(gMemberGroupId);
		when(gGroup.get("id")).thenReturn(mock(Path.class));
		when(gMemberEmbedId.get("userId")).thenReturn(mock(Path.class));

		Path<String> groupName = mock(Path.class);
		Path<String> groupCode = mock(Path.class);
		when(gGroup.<String>get("name")).thenReturn(groupName);
		when(gGroup.<String>get("code")).thenReturn(groupCode);

		Predicate existsRole = mock(Predicate.class);
		Predicate existsGroup = mock(Predicate.class);
		when(cb.exists(roleSubquery)).thenReturn(existsRole);
		when(cb.exists(groupSubquery)).thenReturn(existsGroup);

		Predicate searchOr = mock(Predicate.class);
		Predicate combined = mock(Predicate.class);
		when(cb.or(any(Predicate[].class))).thenReturn(searchOr);
		when(cb.and(any(Predicate[].class))).thenReturn(combined);

		UserFilter filter = UserFilter.ofSearch("cardio");
		Specification<UserJpaEntity> spec = UserSpecifications.withFilter(filter);
		Predicate result = spec.toPredicate(root, query, cb);

		assertThat(result).isSameAs(combined);
		verify(cb).like(lowerEmail, "%cardio%");
		verify(cb).like(lowerName, "%cardio%");
		verify(cb).like(lowerStatus, "%cardio%");
	}
}
