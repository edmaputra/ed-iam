package io.github.edmaputra.iam.application.port.out;

import io.github.edmaputra.iam.domain.event.IamEvent;

/**
 * Outbound port for publishing universal IAM domain events.
 *
 * @author edmaputra
 * @since 0.1.0
 */
@FunctionalInterface
public interface EventPublisherPort {

	/**
	 * Publishes the given domain event to downstream event listeners or message brokers.
	 *
	 * @param event the IAM domain event to publish
	 */
	void publish(IamEvent event);
}
