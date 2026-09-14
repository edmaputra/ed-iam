package io.github.edmaputra.iam.domain.security;

import java.util.Optional;

/**
 * Port for accessing the currently authenticated actor context.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface CurrentActorProvider {

	Optional<CurrentActor> currentActor();

	default CurrentActor requireCurrentActor() {
		return currentActor().orElseThrow(
				() -> new IllegalStateException("An authenticated actor context is required for this operation."));
	}
}
