package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.UserIdentityJpaEntity;

/**
 * Spring Data JPA repository for {@link UserIdentityJpaEntity}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@Repository
public interface UserIdentityJpaRepository extends JpaRepository<UserIdentityJpaEntity, UUID> {

	/**
	 * Finds an identity by provider type and external subject ID.
	 *
	 * @param providerType      the provider type string
	 * @param externalSubjectId the external subject ID
	 * @return optional entity
	 */
	Optional<UserIdentityJpaEntity> findByProviderTypeAndExternalSubjectId(String providerType, String externalSubjectId);

	/**
	 * Finds all identities associated with a user ID.
	 *
	 * @param userId the user ID
	 * @return list of identities
	 */
	List<UserIdentityJpaEntity> findAllByUserId(UUID userId);
}
