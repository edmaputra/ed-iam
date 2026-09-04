package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.persistence.entity.RoleJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.RoleJpaRepository;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.repository.RoleRepository;

/**
 * Persistence adapter implementing {@link RoleRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 */
public class RoleRepositoryAdapter implements RoleRepository {

	private final RoleJpaRepository repository;

	/**
	 * Constructs the adapter with the underlying Spring Data repository.
	 *
	 * @param repository the Spring Data repository
	 */
	public RoleRepositoryAdapter(RoleJpaRepository repository) {
		this.repository = Objects.requireNonNull(repository, "RoleJpaRepository must not be null.");
	}

	@Override
	public Optional<Role> findById(RoleId id) {
		Objects.requireNonNull(id, "RoleId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<Role> findByTenantIdAndCode(TenantId tenantId, String code) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.findByTenantIdAndCodeIgnoreCase(tenantId.value(), code.trim()).map(this::toDomain);
	}

	@Override
	public Optional<Role> findSystemRoleByCode(String code) {
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.findByTenantIdIsNullAndCodeIgnoreCase(code.trim()).map(this::toDomain);
	}

	@Override
	public List<Role> findAllByTenantIdOrGlobal(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return repository.findAllByTenantIdOrGlobal(tenantId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<Role> findAllByIds(Iterable<RoleId> ids) {
		Objects.requireNonNull(ids, "RoleIds must not be null.");
		List<UUID> uuidList = StreamSupport.stream(ids.spliterator(), false)
				.map(RoleId::value)
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
	public Role save(Role role) {
		Objects.requireNonNull(role, "Role must not be null.");
		RoleJpaEntity entity = toEntity(role);
		RoleJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	public void delete(RoleId id) {
		Objects.requireNonNull(id, "RoleId must not be null.");
		repository.deleteById(id.value());
	}

	private Role toDomain(RoleJpaEntity entity) {
		return new Role(
				new RoleId(entity.getId()),
				entity.getTenantId() == null ? null : new TenantId(entity.getTenantId()),
				entity.getCode(),
				entity.getName(),
				entity.getDescription(),
				entity.isSystemRole(),
				entity.getPermissions(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private RoleJpaEntity toEntity(Role role) {
		return new RoleJpaEntity(
				role.getId().value(),
				role.optionalTenantId().map(TenantId::value).orElse(null),
				role.getCode(),
				role.getName(),
				role.optionalDescription().orElse(null),
				role.isSystemRole(),
				role.permissions(),
				role.getCreatedAt(),
				role.getUpdatedAt());
	}
}
