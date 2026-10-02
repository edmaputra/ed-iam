package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.UserMfaJpaEntity;

/**
 * Spring Data JPA repository for {@link UserMfaJpaEntity}.
 *
 * @author edmaputra
 * @since 0.5.0
 */
@Repository
public interface UserMfaJpaRepository extends JpaRepository<UserMfaJpaEntity, UUID> {
}
