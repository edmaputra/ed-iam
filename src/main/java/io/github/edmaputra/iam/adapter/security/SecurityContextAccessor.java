package io.github.edmaputra.iam.adapter.security;

import java.lang.ScopedValue;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

/**
 * Accessor implementing {@link CurrentActorProvider} backed by modern Java 25 {@link ScopedValue}.
 * Provides boundary-safe, thread-local-free scoping of the authenticated actor across virtual and platform threads.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class SecurityContextAccessor implements CurrentActorProvider {

	private static final ScopedValue<CurrentActor> CURRENT_ACTOR = ScopedValue.newInstance();

	@Override
	public Optional<CurrentActor> currentActor() {
		return CURRENT_ACTOR.isBound() ? Optional.of(CURRENT_ACTOR.get()) : Optional.empty();
	}

	/**
	 * Executes an operation within the context of the given {@link CurrentActor}.
	 *
	 * @param actor     the actor to bind
	 * @param operation the operation to execute
	 * @param <T>       the return type
	 * @param <X>       the throwable type
	 * @return the operation result
	 * @throws X if the operation throws an exception
	 */
	public <T, X extends Throwable> T callWithActor(CurrentActor actor, ScopedValue.CallableOp<T, X> operation)
			throws X {
		Objects.requireNonNull(actor, "CurrentActor must not be null.");
		Objects.requireNonNull(operation, "Operation must not be null.");
		return ScopedValue.where(CURRENT_ACTOR, actor).call(operation);
	}
}
