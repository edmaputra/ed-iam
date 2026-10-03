package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.persistence.entity.GroupRoleAssignmentJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.GroupRoleAssignmentJpaRepository;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignmentId;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;

/**
 * Persistence adapter implementing {@link GroupRoleAssignmentRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupRoleAssignmentRepositoryAdapter implements GroupRoleAssignmentRepository {

	private final GroupRoleAssignmentJpaRepository repository;

	@Override
	public Optional<GroupRoleAssignment> findById(GroupRoleAssignmentId id) {
		Objects.requireNonNull(id, "GroupRoleAssignmentId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public List<GroupRoleAssignment> findAllByGroupId(GroupId groupId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		return repository.findAllByGroupId(groupId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<GroupRoleAssignment> findAllByGroupIds(Iterable<GroupId> groupIds) {
		Objects.requireNonNull(groupIds, "GroupIds must not be null.");
		List<UUID> uuidList = StreamSupport.stream(groupIds.spliterator(), false)
				.map(GroupId::value)
				.toList();
		return repository.findAllByGroupIdIn(uuidList).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<GroupRoleAssignment> findAllByTenantId(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return repository.findAllByTenantId(tenantId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	@Transactional
	public GroupRoleAssignment save(GroupRoleAssignment assignment) {
		Objects.requireNonNull(assignment, "GroupRoleAssignment must not be null.");
		GroupRoleAssignmentJpaEntity entity = toEntity(assignment);
		GroupRoleAssignmentJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	@Transactional
	public void delete(GroupRoleAssignmentId id) {
		Objects.requireNonNull(id, "GroupRoleAssignmentId must not be null.");
		repository.deleteById(id.value());
	}

	private GroupRoleAssignment toDomain(GroupRoleAssignmentJpaEntity entity) {
		return new GroupRoleAssignment(
				new GroupRoleAssignmentId(entity.getId()),
				new GroupId(entity.getGroupId()),
				new RoleId(entity.getRoleId()),
				entity.getTenantId() == null ? null : new TenantId(entity.getTenantId()),
				entity.getScopeNodeId() == null ? null : new ScopeNodeId(entity.getScopeNodeId()),
				entity.isInheritChildren(),
				entity.getCreatedAt());
	}

	private GroupRoleAssignmentJpaEntity toEntity(GroupRoleAssignment assignment) {
		return new GroupRoleAssignmentJpaEntity(
				assignment.getId().value(),
				assignment.getGroupId().value(),
				assignment.getRoleId().value(),
				assignment.getTenantId().value(),
				assignment.optionalScopeNodeId().map(ScopeNodeId::value).orElse(null),
				assignment.isInheritChildren(),
				assignment.getCreatedAt());
	}
}
