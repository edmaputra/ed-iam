package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.RedirectUriJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.RedirectUriJpaRepository;
import io.github.edmaputra.iam.domain.repository.RedirectUriRepository;
import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Persistence adapter implementing {@link RedirectUriRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RedirectUriRepositoryAdapter implements RedirectUriRepository {

	private final RedirectUriJpaRepository repository;

	@Override
	public RedirectUriPolicy findByTenantId(TenantId tenantId) {
		UUID tenantUuid = tenantId != null ? tenantId.value() : null;
		List<RedirectUriJpaEntity> entities = tenantUuid != null
				? repository.findByTenantId(tenantUuid)
				: repository.findByTenantIdIsNull();

		Set<String> uris = new LinkedHashSet<>();
		for (RedirectUriJpaEntity entity : entities) {
			uris.add(entity.getUri());
		}
		return new RedirectUriPolicy(uris);
	}

	@Override
	@Transactional
	public RedirectUriPolicy save(TenantId tenantId, RedirectUriPolicy policy) {
		Objects.requireNonNull(policy, "RedirectUriPolicy must not be null.");
		UUID tenantUuid = tenantId != null ? tenantId.value() : null;

		if (tenantUuid != null) {
			repository.deleteByTenantId(tenantUuid);
		}
		else {
			repository.deleteByTenantIdIsNull();
		}

		Instant now = Instant.now();
		Set<String> savedUris = new LinkedHashSet<>();
		for (String uri : policy.allowedUris()) {
			if (uri == null || uri.isBlank()) {
				continue;
			}
			String trimmed = uri.trim();
			RedirectUriJpaEntity entity = new RedirectUriJpaEntity(
					UuidV7.generate(),
					tenantUuid,
					trimmed,
					now,
					now
			);
			repository.save(entity);
			savedUris.add(trimmed);
		}

		return new RedirectUriPolicy(savedUris);
	}

	@Override
	@Transactional
	public void addUri(TenantId tenantId, String uri) {
		if (uri == null || uri.isBlank()) {
			return;
		}
		String trimmed = uri.trim();
		UUID tenantUuid = tenantId != null ? tenantId.value() : null;

		boolean exists = tenantUuid != null
				? repository.findByTenantIdAndUriIgnoreCase(tenantUuid, trimmed).isPresent()
				: repository.findByTenantIdIsNullAndUriIgnoreCase(trimmed).isPresent();

		if (!exists) {
			Instant now = Instant.now();
			RedirectUriJpaEntity entity = new RedirectUriJpaEntity(
					UuidV7.generate(),
					tenantUuid,
					trimmed,
					now,
					now
			);
			repository.save(entity);
		}
	}

	@Override
	@Transactional
	public void removeUri(TenantId tenantId, String uri) {
		if (uri == null || uri.isBlank()) {
			return;
		}
		String trimmed = uri.trim();
		UUID tenantUuid = tenantId != null ? tenantId.value() : null;

		if (tenantUuid != null) {
			repository.deleteByTenantIdAndUriIgnoreCase(tenantUuid, trimmed);
		}
		else {
			repository.deleteByTenantIdIsNullAndUriIgnoreCase(trimmed);
		}
	}
}
