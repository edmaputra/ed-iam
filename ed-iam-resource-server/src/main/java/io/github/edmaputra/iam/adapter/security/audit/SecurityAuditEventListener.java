package io.github.edmaputra.iam.adapter.security.audit;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import io.github.edmaputra.iam.domain.event.IamEvent;

/**
 * Standardized security audit event listener that consumes {@link IamEvent} domain events
 * published by IAM services and outputs structured audit log entries.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class SecurityAuditEventListener {

	private static final Logger log = LoggerFactory.getLogger("io.github.edmaputra.iam.audit");

	private final IamAuditProperties properties;

	public SecurityAuditEventListener(IamAuditProperties properties) {
		this.properties = properties != null ? properties : new IamAuditProperties();
	}

	public SecurityAuditEventListener() {
		this(new IamAuditProperties());
	}

	/**
	 * Consumes and logs security audit events.
	 *
	 * @param event the IAM domain event to audit
	 */
	@EventListener
	public void onSecurityEvent(IamEvent event) {
		if (event == null || !properties.isEnabled() || !properties.isLoggingEnabled()) {
			return;
		}

		log.info("IAM_AUDIT eventType={} tenantId={} entityType={} entityId={} actor={} correlationId={} occurredAt={} payload={}",
				event.eventType(),
				event.tenantId(),
				event.entityType(),
				event.entityId(),
				event.actor(),
				event.correlationId(),
				event.occurredAt(),
				event.payload());
	}
}
