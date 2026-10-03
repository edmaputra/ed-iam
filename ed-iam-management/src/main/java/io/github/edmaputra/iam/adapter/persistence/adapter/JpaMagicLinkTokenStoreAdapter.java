package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.MagicLinkTokenJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.MagicLinkTokenJpaRepository;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.domain.model.MagicLinkId;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Persistence adapter implementing {@link MagicLinkTokenStorePort} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaMagicLinkTokenStoreAdapter implements MagicLinkTokenStorePort {

	private final MagicLinkTokenJpaRepository repository;

	@Override
	@Transactional
	public void save(MagicLinkToken token) {
		Objects.requireNonNull(token, "MagicLinkToken must not be null.");
		MagicLinkTokenJpaEntity entity = toEntity(token);
		repository.save(entity);
	}

	@Override
	public Optional<MagicLinkToken> findByToken(String token) {
		Objects.requireNonNull(token, "Token must not be null.");
		return repository.findByToken(token).map(this::toDomain);
	}

	@Override
	@Transactional
	public Optional<MagicLinkToken> consume(String token, Instant consumedAt) {
		Objects.requireNonNull(token, "Token must not be null.");
		Objects.requireNonNull(consumedAt, "ConsumedAt must not be null.");

		int updated = repository.markConsumed(token, consumedAt);
		if (updated > 0) {
			return repository.findByToken(token).map(this::toDomain);
		}
		return Optional.empty();
	}

	@Override
	@Transactional
	public int deleteExpired(Instant before) {
		Objects.requireNonNull(before, "Before timestamp must not be null.");
		return repository.deleteExpired(before);
	}

	private MagicLinkToken toDomain(MagicLinkTokenJpaEntity entity) {
		TenantId tenantId = entity.getTenantId() != null ? new TenantId(UUID.fromString(entity.getTenantId())) : null;
		return new MagicLinkToken(
				new MagicLinkId(entity.getId()),
				entity.getToken(),
				new UserId(entity.getUserId()),
				tenantId,
				entity.getEmail(),
				entity.getExpiresAt(),
				entity.getConsumedAt(),
				entity.getCreatedAt());
	}

	private MagicLinkTokenJpaEntity toEntity(MagicLinkToken token) {
		String tenantId = token.tenantId() != null ? token.tenantId().value().toString() : null;
		return new MagicLinkTokenJpaEntity(
				token.id().value(),
				token.token(),
				token.userId().value(),
				tenantId,
				token.email(),
				token.expiresAt(),
				token.consumedAt(),
				token.createdAt());
	}
}
