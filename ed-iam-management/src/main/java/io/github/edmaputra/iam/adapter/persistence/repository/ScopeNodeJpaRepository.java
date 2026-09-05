package io.github.edmaputra.iam.adapter.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.edmaputra.iam.adapter.persistence.entity.ScopeNodeEntity;

/**
 * Spring Data JPA repository for IAM scope hierarchy nodes ({@link ScopeNodeEntity}).
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface ScopeNodeJpaRepository extends JpaRepository<ScopeNodeEntity, UUID> {

	/**
	 * Finds all scope nodes belonging to a specific tenant.
	 *
	 * @param tenantId the tenant ID
	 * @return list of matching scope node entities
	 */
	List<ScopeNodeEntity> findAllByTenantId(UUID tenantId);

	/**
	 * Finds a scope node by tenant ID and unique code.
	 *
	 * @param tenantId the tenant ID
	 * @param code the scope node code
	 * @return optional containing the matching entity, or empty
	 */
	Optional<ScopeNodeEntity> findByTenantIdAndCode(UUID tenantId, String code);

	/**
	 * Finds a scope node by tenant ID and materialized path.
	 *
	 * @param tenantId the tenant ID
	 * @param path     the materialized path
	 * @return optional containing the matching entity, or empty
	 */
	Optional<ScopeNodeEntity> findByTenantIdAndPath(UUID tenantId, String path);

	/**
	 * Finds all root scope nodes for a tenant (parentId is null).
	 *
	 * @param tenantId the tenant ID
	 * @return list of root scope node entities
	 */
	List<ScopeNodeEntity> findAllByTenantIdAndParentIdIsNull(UUID tenantId);

	/**
	 * Finds all direct child nodes of a parent scope node.
	 *
	 * @param parentId the parent scope node ID
	 * @return list of direct children
	 */
	List<ScopeNodeEntity> findByParentId(UUID parentId);

	/**
	 * Finds all direct child nodes of a parent scope node.
	 *
	 * @param parentId the parent scope node ID
	 * @return list of direct children
	 */
	List<ScopeNodeEntity> findAllByParentId(UUID parentId);

	/**
	 * Finds all descendant scope nodes whose materialized path starts with the given prefix.
	 *
	 * @param pathPrefix the materialized path prefix
	 * @return list of descendant entities
	 */
	List<ScopeNodeEntity> findByPathStartingWith(String pathPrefix);

	/**
	 * Checks if any child node exists for a given parent ID.
	 *
	 * @param parentId the parent scope node ID
	 * @return {@code true} if children exist, {@code false} otherwise
	 */
	boolean existsByParentId(UUID parentId);

	/**
	 * Checks if a scope node with the specified code already exists within a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @param code the scope node code
	 * @return {@code true} if code exists, {@code false} otherwise
	 */
	boolean existsByTenantIdAndCode(UUID tenantId, String code);

	/**
	 * Checks if a root scope node exists with the given code.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the scope node code
	 * @return true if exists
	 */
	boolean existsByTenantIdAndCodeAndParentIdIsNull(UUID tenantId, String code);

	/**
	 * Checks if a child scope node exists under a parent with the given code.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the scope node code
	 * @param parentId the parent ID
	 * @return true if exists
	 */
	boolean existsByTenantIdAndCodeAndParentId(UUID tenantId, String code, UUID parentId);

	/**
	 * Batch updates the materialized path prefix of all descendant scope nodes when a subtree is moved.
	 *
	 * @param oldPrefix the old materialized path prefix
	 * @param newPrefix the new materialized path prefix
	 * @param now timestamp for updatedAt
	 * @return number of updated entities
	 */
	@Modifying
	@Query("UPDATE ScopeNodeEntity s SET s.path = CONCAT(:newPrefix, SUBSTRING(s.path, LENGTH(:oldPrefix) + 1)), s.updatedAt = :now WHERE s.path LIKE CONCAT(:oldPrefix, '%')")
	int updatePathPrefix(
			@Param("oldPrefix") String oldPrefix,
			@Param("newPrefix") String newPrefix,
			@Param("now") Instant now);
}
