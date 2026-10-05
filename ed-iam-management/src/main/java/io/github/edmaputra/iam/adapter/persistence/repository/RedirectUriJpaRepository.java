package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import io.github.edmaputra.iam.adapter.persistence.entity.RedirectUriJpaEntity;

/**
 * Spring Data JPA repository for {@link RedirectUriJpaEntity}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface RedirectUriJpaRepository extends JpaRepository<RedirectUriJpaEntity, UUID> {

	List<RedirectUriJpaEntity> findByTenantId(UUID tenantId);

	List<RedirectUriJpaEntity> findByTenantIdIsNull();

	Optional<RedirectUriJpaEntity> findByTenantIdAndUriIgnoreCase(UUID tenantId, String uri);

	Optional<RedirectUriJpaEntity> findByTenantIdIsNullAndUriIgnoreCase(String uri);

	@Modifying
	void deleteByTenantId(UUID tenantId);

	@Modifying
	void deleteByTenantIdIsNull();

	@Modifying
	void deleteByTenantIdAndUriIgnoreCase(UUID tenantId, String uri);

	@Modifying
	void deleteByTenantIdIsNullAndUriIgnoreCase(String uri);
}
