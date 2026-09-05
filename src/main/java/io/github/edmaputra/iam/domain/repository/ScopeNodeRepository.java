package io.github.edmaputra.iam.domain.repository;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;

/**
 * Outbound repository port for ScopeNode domain entity.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface ScopeNodeRepository {

	ScopeNode save(ScopeNode node);

	Optional<ScopeNode> findById(ScopeNodeId id);

	Optional<ScopeNode> findByTenantIdAndCode(TenantId tenantId, String code);

	List<ScopeNode> findAllByTenantId(TenantId tenantId);

	List<ScopeNode> findByParentId(ScopeNodeId parentId);

	List<ScopeNode> findDescendantsByPathPrefix(String pathPrefix);

	void updatePathPrefix(String oldPrefix, String newPrefix);

	boolean existsByParentId(ScopeNodeId parentId);

	boolean existsByTenantIdAndCode(TenantId tenantId, String code);

	void delete(ScopeNodeId id);
}
