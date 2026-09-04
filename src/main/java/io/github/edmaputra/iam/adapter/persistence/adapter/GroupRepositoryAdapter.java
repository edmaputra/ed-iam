package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.persistence.entity.GroupJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.GroupJpaRepository;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.repository.GroupRepository;

/**
 * Persistence adapter implementing {@link GroupRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 */
public class GroupRepositoryAdapter implements GroupRepository {

	private final GroupJpaRepository repository;

	/**
	 * Constructs the adapter with the underlying Spring Data repository.
	 *
	 * @param repository the Spring Data repository
	 */
	public GroupRepositoryAdapter(GroupJpaRepository repository) {
		this.repository = Objects.requireNonNull(repository, "GroupJpaRepository must not be null.");
	}

	@Override
	public Optional<Group> findById(GroupId id) {
		Objects.requireNonNull(id, "GroupId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<Group> findByTenantIdAndCode(TenantId tenantId, String code) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.findByTenantIdAndCodeIgnoreCase(tenantId.value(), code.trim()).map(this::toDomain);
	}

	@Override
	public List<Group> findAllByTenantId(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return repository.findAllByTenantId(tenantId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<Group> findAllByIds(Iterable<GroupId> ids) {
		Objects.requireNonNull(ids, "GroupIds must not be null.");
		List<UUID> uuidList = StreamSupport.stream(ids.spliterator(), false)
				.map(GroupId::value)
				.toList();
		return repository.findAllById(uuidList).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public boolean existsByTenantIdAndCode(TenantId tenantId, String code) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.existsByTenantIdAndCodeIgnoreCase(tenantId.value(), code.trim());
	}

	@Override
	public Optional<Group> findByTenantIdAndExternalIdpGroupName(TenantId tenantId, String externalIdpGroupName) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(externalIdpGroupName, "ExternalIdpGroupName must not be null.");
		return repository.findByTenantIdAndExternalIdpGroupNameIgnoreCase(tenantId.value(), externalIdpGroupName.trim())
				.map(this::toDomain);
	}

	@Override
	public Group save(Group group) {
		Objects.requireNonNull(group, "Group must not be null.");
		GroupJpaEntity entity = toEntity(group);
		GroupJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	public void delete(GroupId id) {
		Objects.requireNonNull(id, "GroupId must not be null.");
		repository.deleteById(id.value());
	}

	private Group toDomain(GroupJpaEntity entity) {
		return new Group(
				new GroupId(entity.getId()),
				new TenantId(entity.getTenantId()),
				entity.getCode(),
				entity.getName(),
				entity.getDescription(),
				entity.getExternalIdpGroupName(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private GroupJpaEntity toEntity(Group group) {
		return new GroupJpaEntity(
				group.getId().value(),
				group.getTenantId().value(),
				group.getCode(),
				group.getName(),
				group.optionalDescription().orElse(null),
				group.optionalExternalIdpGroupName().orElse(null),
				group.getCreatedAt(),
				group.getUpdatedAt());
	}
}
