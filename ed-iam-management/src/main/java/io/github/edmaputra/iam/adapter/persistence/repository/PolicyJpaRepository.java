package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.edmaputra.iam.adapter.persistence.entity.PolicyJpaEntity;

/**
 * Spring Data JPA repository for {@link PolicyJpaEntity}.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public interface PolicyJpaRepository extends JpaRepository<PolicyJpaEntity, UUID> {

	Optional<PolicyJpaEntity> findByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

	Optional<PolicyJpaEntity> findByTenantIdIsNullAndNameIgnoreCase(String name);

	List<PolicyJpaEntity> findByTenantIdOrderByPriorityDesc(UUID tenantId);

	List<PolicyJpaEntity> findByTenantIdIsNullOrderByPriorityDesc();

	@Query("SELECT p FROM PolicyJpaEntity p WHERE p.tenantId = :tenantId OR p.tenantId IS NULL ORDER BY p.priority DESC")
	List<PolicyJpaEntity> findAllByTenantIdOrGlobal(@Param("tenantId") UUID tenantId);

	@Query("SELECT p FROM PolicyJpaEntity p WHERE (p.tenantId = :tenantId OR p.tenantId IS NULL) AND p.enabled = true ORDER BY p.priority DESC")
	List<PolicyJpaEntity> findAllActiveByTenantIdOrGlobal(@Param("tenantId") UUID tenantId);

	@Query("SELECT p FROM PolicyJpaEntity p WHERE p.tenantId IS NULL AND p.enabled = true ORDER BY p.priority DESC")
	List<PolicyJpaEntity> findAllActiveGlobal();

	boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

	boolean existsByTenantIdIsNullAndNameIgnoreCase(String name);
}
