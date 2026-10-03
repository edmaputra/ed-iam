package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.UserGroupMembershipJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserGroupMembershipJpaId;
import io.github.edmaputra.iam.adapter.persistence.repository.UserGroupMembershipJpaRepository;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;

/**
 * Persistence adapter implementing {@link UserGroupMembershipRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserGroupMembershipRepositoryAdapter implements UserGroupMembershipRepository {

	private final UserGroupMembershipJpaRepository repository;

	@Override
	public List<UserGroupMembership> findAllByUserId(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return repository.findAllByIdUserId(userId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<UserGroupMembership> findAllByGroupId(GroupId groupId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		return repository.findAllByIdGroupId(groupId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public boolean existsByGroupIdAndUserId(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");
		return repository.existsByIdGroupIdAndIdUserId(groupId.value(), userId.value());
	}

	@Override
	@Transactional
	public UserGroupMembership save(UserGroupMembership membership) {
		Objects.requireNonNull(membership, "UserGroupMembership must not be null.");
		UserGroupMembershipJpaEntity entity = toEntity(membership);
		UserGroupMembershipJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	@Transactional
	public void delete(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");
		repository.deleteById(new UserGroupMembershipJpaId(groupId.value(), userId.value()));
	}

	private UserGroupMembership toDomain(UserGroupMembershipJpaEntity entity) {
		return new UserGroupMembership(
				new GroupId(entity.getId().getGroupId()),
				new UserId(entity.getId().getUserId()),
				entity.getJoinedAt());
	}

	private UserGroupMembershipJpaEntity toEntity(UserGroupMembership membership) {
		return new UserGroupMembershipJpaEntity(
				new UserGroupMembershipJpaId(membership.groupId().value(), membership.userId().value()),
				membership.joinedAt());
	}
}
