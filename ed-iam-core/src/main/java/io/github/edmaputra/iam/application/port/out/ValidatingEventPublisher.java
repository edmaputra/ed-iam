package io.github.edmaputra.iam.application.port.out;

import java.util.Objects;

import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventValidator;

/**
 * Decorating implementation of {@link EventPublisherPort} that validates domain events
 * and sanitizes sensitive payload data before delegating to the target publisher.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class ValidatingEventPublisher implements EventPublisherPort {

	private final EventPublisherPort delegate;
	private final IamEventValidator validator;

	public ValidatingEventPublisher(EventPublisherPort delegate, IamEventValidator validator) {
		this.delegate = delegate;
		this.validator = Objects.requireNonNull(validator, "IamEventValidator must not be null.");
	}

	public ValidatingEventPublisher(EventPublisherPort delegate) {
		this(delegate, new IamEventValidator());
	}

	/**
	 * Creates a validating event publisher wrapping the given delegate.
	 *
	 * @param delegate outbound event publisher delegate (may be null for no-op)
	 * @return validating event publisher instance
	 */
	public static ValidatingEventPublisher of(EventPublisherPort delegate) {
		return new ValidatingEventPublisher(delegate);
	}

	/**
	 * Creates a safe no-op validating event publisher.
	 *
	 * @return no-op validating event publisher instance
	 */
	public static ValidatingEventPublisher noop() {
		return new ValidatingEventPublisher(null);
	}

	@Override
	public void publish(IamEvent event) {
		if (event == null || delegate == null) {
			return;
		}

		IamEvent sanitized = validator.validateAndSanitize(event);
		delegate.publish(sanitized);
	}
}
