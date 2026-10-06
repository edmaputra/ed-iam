package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.edmaputra.iam.adapter.persistence.entity.PasswordPolicyJpaEntity;

/**
 * Spring Data JPA repository for {@link PasswordPolicyJpaEntity}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface PasswordPolicyJpaRepository extends JpaRepository<PasswordPolicyJpaEntity, UUID> {

	/**
	 * Finds password policy entity configured for a specific tenant.
	 *
	 * @param tenantId tenant identifier
	 * @return optional entity
	 */
	Optional<PasswordPolicyJpaEntity> findByTenantId(UUID tenantId);

	/**
	 * Finds default global password policy entity when tenant ID is null.
	 *
	 * @return optional entity
	 */
	Optional<PasswordPolicyJpaEntity> findByTenantIdIsNull();
}
