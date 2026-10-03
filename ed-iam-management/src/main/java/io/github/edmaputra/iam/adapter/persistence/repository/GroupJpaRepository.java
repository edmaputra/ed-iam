package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.edmaputra.iam.adapter.persistence.entity.GroupJpaEntity;

/**
 * Spring Data JPA repository for {@link GroupJpaEntity}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface GroupJpaRepository extends JpaRepository<GroupJpaEntity, UUID> {

	/**
	 * Finds a group by tenant ID and code.
	 *
	 * @param tenantId the tenant UUID
	 * @param code     the group code
	 * @return optional entity
	 */
	Optional<GroupJpaEntity> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

	/**
	 * Finds all groups in a tenant.
	 *
	 * @param tenantId the tenant UUID
	 * @return list of groups
	 */
	List<GroupJpaEntity> findAllByTenantId(UUID tenantId);

	/**
	 * Checks existence by tenant ID and code.
	 *
	 * @param tenantId the tenant UUID
	 * @param code     the group code
	 * @return true if exists
	 */
	boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

	/**
	 * Finds a group mapped to an external IdP group claim name.
	 *
	 * @param tenantId             the tenant UUID
	 * @param externalIdpGroupName the external group name
	 * @return optional entity
	 */
	Optional<GroupJpaEntity> findByTenantIdAndExternalIdpGroupNameIgnoreCase(UUID tenantId,
			String externalIdpGroupName);
}
