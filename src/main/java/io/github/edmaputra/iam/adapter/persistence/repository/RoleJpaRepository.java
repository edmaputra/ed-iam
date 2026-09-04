package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.RoleJpaEntity;

/**
 * Spring Data JPA repository for {@link RoleJpaEntity}.
 *
 * @author edmaputra
 */
@Repository
public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

	/**
	 * Finds a tenant-specific role by tenant ID and code.
	 *
	 * @param tenantId the tenant UUID
	 * @param code     the role code
	 * @return optional entity
	 */
	Optional<RoleJpaEntity> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

	/**
	 * Finds a global system role by code.
	 *
	 * @param code the system role code
	 * @return optional entity
	 */
	Optional<RoleJpaEntity> findByTenantIdIsNullAndCodeIgnoreCase(String code);

	/**
	 * Finds all roles available to a tenant or globally configured.
	 *
	 * @param tenantId the tenant UUID
	 * @return list of role entities
	 */
	@Query("SELECT r FROM RoleJpaEntity r WHERE r.tenantId = :tenantId OR r.tenantId IS NULL")
	List<RoleJpaEntity> findAllByTenantIdOrGlobal(@Param("tenantId") UUID tenantId);

	/**
	 * Checks existence of a role by tenant ID and code.
	 *
	 * @param tenantId the tenant UUID
	 * @param code     the role code
	 * @return true if exists
	 */
	boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
