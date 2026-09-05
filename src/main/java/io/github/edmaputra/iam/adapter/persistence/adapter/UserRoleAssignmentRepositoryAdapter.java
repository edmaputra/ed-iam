package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.persistence.entity.UserRoleAssignmentJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserRoleAssignmentJpaRepository;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Persistence adapter implementing {@link UserRoleAssignmentRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class UserRoleAssignmentRepositoryAdapter implements UserRoleAssignmentRepository {

	private final UserRoleAssignmentJpaRepository repository;

	/**
	 * Constructs the adapter with the underlying Spring Data repository.
	 *
	 * @param repository the Spring Data repository
	 */
	public UserRoleAssignmentRepositoryAdapter(UserRoleAssignmentJpaRepository repository) {
		this.repository = Objects.requireNonNull(repository, "UserRoleAssignmentJpaRepository must not be null.");
	}

	@Override
	public Optional<UserRoleAssignment> findById(UserRoleAssignmentId id) {
		Objects.requireNonNull(id, "UserRoleAssignmentId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public List<UserRoleAssignment> findAllByUserId(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return repository.findAllByUserId(userId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<UserRoleAssignment> findAllByUserIdAndTenantId(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return repository.findAllByUserIdAndTenantId(userId.value(), tenantId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public UserRoleAssignment save(UserRoleAssignment assignment) {
		Objects.requireNonNull(assignment, "UserRoleAssignment must not be null.");
		UserRoleAssignmentJpaEntity entity = toEntity(assignment);
		UserRoleAssignmentJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	public void delete(UserRoleAssignmentId id) {
		Objects.requireNonNull(id, "UserRoleAssignmentId must not be null.");
		repository.deleteById(id.value());
	}

	private UserRoleAssignment toDomain(UserRoleAssignmentJpaEntity entity) {
		return new UserRoleAssignment(
				new UserRoleAssignmentId(entity.getId()),
				new UserId(entity.getUserId()),
				new RoleId(entity.getRoleId()),
				entity.getTenantId() == null ? null : new TenantId(entity.getTenantId()),
				entity.getScopeNodeId() == null ? null : new ScopeNodeId(entity.getScopeNodeId()),
				entity.isInheritChildren(),
				entity.getCreatedAt());
	}

	private UserRoleAssignmentJpaEntity toEntity(UserRoleAssignment assignment) {
		return new UserRoleAssignmentJpaEntity(
				assignment.getId().value(),
				assignment.getUserId().value(),
				assignment.getRoleId().value(),
				assignment.optionalTenantId().map(TenantId::value).orElse(null),
				assignment.optionalScopeNodeId().map(ScopeNodeId::value).orElse(null),
				assignment.isInheritChildren(),
				assignment.getCreatedAt());
	}
}
