package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.persistence.entity.ScopeNodeEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.ScopeNodeJpaRepository;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;

/**
 * Persistence adapter implementing {@link ScopeNodeRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 */
public class ScopeNodeRepositoryAdapter implements ScopeNodeRepository {

	private final ScopeNodeJpaRepository repository;

	/**
	 * Constructs the adapter with the underlying Spring Data repository.
	 *
	 * @param repository the Spring Data repository
	 */
	public ScopeNodeRepositoryAdapter(ScopeNodeJpaRepository repository) {
		this.repository = Objects.requireNonNull(repository, "ScopeNodeJpaRepository must not be null.");
	}

	@Override
	public ScopeNode save(ScopeNode node) {
		Objects.requireNonNull(node, "ScopeNode must not be null.");
		ScopeNodeEntity entity = toEntity(node);
		ScopeNodeEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	public Optional<ScopeNode> findById(ScopeNodeId id) {
		Objects.requireNonNull(id, "ScopeNodeId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<ScopeNode> findByTenantIdAndCode(TenantId tenantId, String code) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.findByTenantIdAndCode(tenantId.value(), code.trim().toUpperCase()).map(this::toDomain);
	}

	@Override
	public List<ScopeNode> findAllByTenantId(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return repository.findAllByTenantId(tenantId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<ScopeNode> findByParentId(ScopeNodeId parentId) {
		Objects.requireNonNull(parentId, "ScopeNodeId must not be null.");
		return repository.findByParentId(parentId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<ScopeNode> findDescendantsByPathPrefix(String pathPrefix) {
		Objects.requireNonNull(pathPrefix, "PathPrefix must not be null.");
		return repository.findByPathStartingWith(pathPrefix).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public void updatePathPrefix(String oldPrefix, String newPrefix) {
		Objects.requireNonNull(oldPrefix, "OldPrefix must not be null.");
		Objects.requireNonNull(newPrefix, "NewPrefix must not be null.");
		repository.updatePathPrefix(oldPrefix, newPrefix, Instant.now());
	}

	@Override
	public boolean existsByParentId(ScopeNodeId parentId) {
		Objects.requireNonNull(parentId, "ScopeNodeId must not be null.");
		return repository.existsByParentId(parentId.value());
	}

	@Override
	public boolean existsByTenantIdAndCode(TenantId tenantId, String code) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		return repository.existsByTenantIdAndCode(tenantId.value(), code.trim());
	}

	@Override
	public void delete(ScopeNodeId id) {
		Objects.requireNonNull(id, "ScopeNodeId must not be null.");
		repository.deleteById(id.value());
	}

	private ScopeNode toDomain(ScopeNodeEntity entity) {
		return new ScopeNode(
				new ScopeNodeId(entity.getId()),
				new TenantId(entity.getTenantId()),
				entity.getParentId() == null ? null : new ScopeNodeId(entity.getParentId()),
				entity.getCode(),
				entity.getName(),
				entity.getPath(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private ScopeNodeEntity toEntity(ScopeNode node) {
		return new ScopeNodeEntity(
				node.getId().value(),
				node.getTenantId().value(),
				node.optionalParentId().map(ScopeNodeId::value).orElse(null),
				node.getCode(),
				node.getName(),
				node.getPath(),
				node.getCreatedAt(),
				node.getUpdatedAt());
	}
}
