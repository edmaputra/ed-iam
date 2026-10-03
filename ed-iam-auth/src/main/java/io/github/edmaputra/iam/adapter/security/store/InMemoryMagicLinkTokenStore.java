package io.github.edmaputra.iam.adapter.security.store;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;

/**
 * In-memory thread-safe implementation of {@link MagicLinkTokenStorePort}.
 * Useful for local testing, development, and standalone microservices.
 *
 * @author edmaputra
 * @since 0.7.0
 */
public class InMemoryMagicLinkTokenStore implements MagicLinkTokenStorePort {

	private final Map<String, MagicLinkToken> tokens = new ConcurrentHashMap<>();

	@Override
	public void save(MagicLinkToken token) {
		Objects.requireNonNull(token, "MagicLinkToken must not be null.");
		tokens.put(token.token(), token);
	}

	@Override
	public Optional<MagicLinkToken> findByToken(String token) {
		Objects.requireNonNull(token, "Token must not be null.");
		return Optional.ofNullable(tokens.get(token));
	}

	@Override
	public Optional<MagicLinkToken> consume(String token, Instant consumedAt) {
		Objects.requireNonNull(token, "Token must not be null.");
		Objects.requireNonNull(consumedAt, "ConsumedAt must not be null.");

		AtomicReference<MagicLinkToken> result = new AtomicReference<>();
		tokens.computeIfPresent(token, (k, existing) -> {
			if (!existing.isConsumed() && !existing.isExpired(consumedAt)) {
				MagicLinkToken consumed = existing.consume(consumedAt);
				result.set(consumed);
				return consumed;
			}
			return existing;
		});

		return Optional.ofNullable(result.get());
	}

	@Override
	public int deleteExpired(Instant before) {
		Objects.requireNonNull(before, "Before timestamp must not be null.");
		int count = 0;
		for (Map.Entry<String, MagicLinkToken> entry : tokens.entrySet()) {
			if (entry.getValue().isExpired(before) || entry.getValue().isConsumed()) {
				if (tokens.remove(entry.getKey(), entry.getValue())) {
					count++;
				}
			}
		}
		return count;
	}
}
