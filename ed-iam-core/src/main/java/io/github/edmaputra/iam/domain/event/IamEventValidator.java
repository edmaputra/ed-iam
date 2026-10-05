package io.github.edmaputra.iam.domain.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validator and data sanitizer for {@link IamEvent} security domain events.
 * Ensures domain events conform to formatting constraints and prevents accidental
 * leakage of sensitive credentials or secrets into SIEM audit streams.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class IamEventValidator {

	private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^[A-Z0-9_.-]+$");
	private static final String MASKED_VALUE = "[PROTECTED]";

	private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
			"password",
			"secret",
			"rawpassword",
			"clientsecret",
			"token",
			"accesstoken",
			"refreshtoken",
			"authorization",
			"credentials"
	);

	/**
	 * Validates the core structural invariants of an IAM domain event.
	 *
	 * @param event the event to validate
	 * @throws IllegalArgumentException if any required property violates domain invariants
	 */
	public void validate(IamEvent event) {
		Objects.requireNonNull(event, "IamEvent must not be null.");

		if (event.eventType() == null || event.eventType().isBlank()) {
			throw new IllegalArgumentException("IamEvent eventType must not be blank.");
		}

		if (!EVENT_TYPE_PATTERN.matcher(event.eventType()).matches()) {
			throw new IllegalArgumentException("IamEvent eventType '" + event.eventType()
					+ "' contains invalid characters. Must be uppercase alphanumeric, underscores, dots, or hyphens.");
		}

		Objects.requireNonNull(event.entityId(), "IamEvent entityId must not be null.");

		if (event.entityType() == null || event.entityType().isBlank()) {
			throw new IllegalArgumentException("IamEvent entityType must not be blank.");
		}

		if (event.actor() == null || event.actor().isBlank()) {
			throw new IllegalArgumentException("IamEvent actor must not be blank.");
		}

		Objects.requireNonNull(event.occurredAt(), "IamEvent occurredAt must not be null.");
	}

	/**
	 * Sanitizes the event payload by masking sensitive credential keys.
	 *
	 * @param event the event to sanitize
	 * @return a sanitized event instance
	 */
	public IamEvent sanitize(IamEvent event) {
		if (event == null) {
			return null;
		}

		Object payload = event.payload();
		if (!(payload instanceof Map<?, ?> rawMap)) {
			return event;
		}

		Map<String, Object> sanitizedMap = sanitizeMap(rawMap);
		return new IamEvent(
				event.eventType(),
				event.tenantId(),
				event.entityId(),
				event.entityType(),
				Collections.unmodifiableMap(sanitizedMap),
				event.actor(),
				event.correlationId(),
				event.occurredAt()
		);
	}

	/**
	 * Validates and sanitizes the given domain event.
	 *
	 * @param event the event to validate and sanitize
	 * @return validated and sanitized event
	 */
	public IamEvent validateAndSanitize(IamEvent event) {
		validate(event);
		return sanitize(event);
	}

	private Map<String, Object> sanitizeMap(Map<?, ?> original) {
		Map<String, Object> copy = new HashMap<>(original.size());
		for (Map.Entry<?, ?> entry : original.entrySet()) {
			String key = String.valueOf(entry.getKey());
			Object val = entry.getValue();

			if (isSensitiveKey(key)) {
				copy.put(key, MASKED_VALUE);
			} else if (val instanceof Map<?, ?> nestedMap) {
				copy.put(key, sanitizeMap(nestedMap));
			} else {
				copy.put(key, val);
			}
		}
		return copy;
	}

	private boolean isSensitiveKey(String key) {
		if (key == null) {
			return false;
		}
		String normalized = key.toLowerCase().replaceAll("[^a-z0-9]", "");
		return SENSITIVE_KEY_NAMES.contains(normalized);
	}
}
