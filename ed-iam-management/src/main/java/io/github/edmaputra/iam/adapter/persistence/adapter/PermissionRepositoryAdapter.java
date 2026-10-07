package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.PermissionJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.PermissionJpaRepository;
import io.github.edmaputra.iam.domain.model.Permission;
import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.repository.PermissionRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Persistence adapter implementing {@link PermissionRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.10.0
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionRepositoryAdapter implements PermissionRepository {

	private final PermissionJpaRepository repository;

	@Override
	public Optional<Permission> findById(PermissionId id) {
		Objects.requireNonNull(id, "PermissionId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<Permission> findByTenantIdAndCode(TenantId tenantId, String code) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.findByTenantIdAndCodeIgnoreCase(tenantId.value(), code.trim()).map(this::toDomain);
	}

	@Override
	public Optional<Permission> findSystemPermissionByCode(String code) {
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.findByTenantIdIsNullAndCodeIgnoreCase(code.trim()).map(this::toDomain);
	}

	@Override
	public Optional<Permission> findByTenantIdOrGlobalByCode(TenantId tenantId, String code) {
		Objects.requireNonNull(code, "Code must not be null.");
		UUID tenantUuid = tenantId != null ? tenantId.value() : null;
		if (tenantUuid != null) {
			return repository.findByTenantIdOrGlobalByCode(tenantUuid, code.trim()).map(this::toDomain);
		}
		return repository.findByTenantIdIsNullAndCodeIgnoreCase(code.trim()).map(this::toDomain);
	}

	@Override
	public List<Permission> findAllByTenantIdOrGlobal(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return repository.findAllByTenantIdOrGlobal(tenantId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<Permission> findAllGlobal() {
		return repository.findByTenantIdIsNull().stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<Permission> findAllByTenantIdOrGlobalAndCategory(TenantId tenantId, String category) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(category, "Category must not be null.");
		return repository.findAllByTenantIdOrGlobalAndCategory(tenantId.value(), category.trim()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<Permission> findAllGlobalAndCategory(String category) {
		Objects.requireNonNull(category, "Category must not be null.");
		return repository.findAllGlobalAndCategory(category.trim()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<Permission> findAllByIds(Iterable<PermissionId> ids) {
		Objects.requireNonNull(ids, "PermissionIds must not be null.");
		List<UUID> uuidList = StreamSupport.stream(ids.spliterator(), false)
				.map(PermissionId::value)
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
	public boolean existsSystemPermissionByCode(String code) {
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.existsByTenantIdIsNullAndCodeIgnoreCase(code.trim());
	}

	@Override
	@Transactional
	public Permission save(Permission permission) {
		Objects.requireNonNull(permission, "Permission must not be null.");
		PermissionJpaEntity entity = toEntity(permission);
		PermissionJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	@Transactional
	public void delete(PermissionId id) {
		Objects.requireNonNull(id, "PermissionId must not be null.");
		repository.deleteById(id.value());
	}

	private Permission toDomain(PermissionJpaEntity entity) {
		return new Permission(
				new PermissionId(entity.getId()),
				entity.getTenantId() == null ? null : new TenantId(entity.getTenantId()),
				entity.getCode(),
				entity.getName(),
				entity.getDescription(),
				entity.getCategory(),
				entity.isSystemPermission(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private PermissionJpaEntity toEntity(Permission permission) {
		return new PermissionJpaEntity(
				permission.id().value(),
				permission.optionalTenantId().map(TenantId::value).orElse(null),
				permission.code(),
				permission.name(),
				permission.optionalDescription().orElse(null),
				permission.category(),
				permission.systemPermission(),
				permission.createdAt(),
				permission.updatedAt());
	}
}
