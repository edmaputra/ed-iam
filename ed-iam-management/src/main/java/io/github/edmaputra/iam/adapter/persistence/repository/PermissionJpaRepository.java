package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.edmaputra.iam.adapter.persistence.entity.PermissionJpaEntity;

/**
 * Spring Data JPA repository for {@link PermissionJpaEntity}.
 *
 * @author edmaputra
 * @since 0.10.0
 */
public interface PermissionJpaRepository extends JpaRepository<PermissionJpaEntity, UUID> {

	/**
	 * Finds a tenant-specific permission by tenant ID and code.
	 *
	 * @param tenantId the tenant UUID
	 * @param code the permission code
	 * @return optional entity
	 */
	Optional<PermissionJpaEntity> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

	/**
	 * Finds a global system permission by code.
	 *
	 * @param code the system permission code
	 * @return optional entity
	 */
	Optional<PermissionJpaEntity> findByTenantIdIsNullAndCodeIgnoreCase(String code);

	/**
	 * Finds a permission by code either belonging to the tenant or globally available.
	 *
	 * @param tenantId the tenant UUID
	 * @param code the permission code
	 * @return optional entity
	 */
	@Query("SELECT p FROM PermissionJpaEntity p WHERE (p.tenantId = :tenantId OR p.tenantId IS NULL) AND LOWER(p.code) = LOWER(:code)")
	Optional<PermissionJpaEntity> findByTenantIdOrGlobalByCode(@Param("tenantId") UUID tenantId, @Param("code") String code);

	/**
	 * Finds all permissions available to a tenant or globally configured.
	 *
	 * @param tenantId the tenant UUID
	 * @return list of permission entities
	 */
	@Query("SELECT p FROM PermissionJpaEntity p WHERE p.tenantId = :tenantId OR p.tenantId IS NULL")
	List<PermissionJpaEntity> findAllByTenantIdOrGlobal(@Param("tenantId") UUID tenantId);

	/**
	 * Finds all global system permissions.
	 *
	 * @return list of global permission entities
	 */
	List<PermissionJpaEntity> findByTenantIdIsNull();

	/**
	 * Finds all permissions available to a tenant or globally configured with a given category.
	 *
	 * @param tenantId the tenant UUID
	 * @param category category name
	 * @return list of permission entities
	 */
	@Query("SELECT p FROM PermissionJpaEntity p WHERE (p.tenantId = :tenantId OR p.tenantId IS NULL) AND LOWER(p.category) = LOWER(:category)")
	List<PermissionJpaEntity> findAllByTenantIdOrGlobalAndCategory(@Param("tenantId") UUID tenantId, @Param("category") String category);

	/**
	 * Finds all global permissions with a given category.
	 *
	 * @param category category name
	 * @return list of global permission entities
	 */
	@Query("SELECT p FROM PermissionJpaEntity p WHERE p.tenantId IS NULL AND LOWER(p.category) = LOWER(:category)")
	List<PermissionJpaEntity> findAllGlobalAndCategory(@Param("category") String category);

	/**
	 * Checks existence of a permission by tenant ID and code.
	 *
	 * @param tenantId the tenant UUID
	 * @param code the permission code
	 * @return true if exists
	 */
	boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

	/**
	 * Checks existence of a global system permission by code.
	 *
	 * @param code the permission code
	 * @return true if exists
	 */
	boolean existsByTenantIdIsNullAndCodeIgnoreCase(String code);
}
