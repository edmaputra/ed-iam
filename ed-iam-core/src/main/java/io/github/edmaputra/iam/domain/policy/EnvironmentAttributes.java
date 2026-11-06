package io.github.edmaputra.iam.domain.policy;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates contextual and environmental attributes captured at request time.
 *
 * @param clientIp client IP address (IPv4 or IPv6)
 * @param timestamp evaluation timestamp
 * @param userAgent client User-Agent header
 * @param devicePosture client device security posture telemetry
 * @param attributes arbitrary additional environment attributes
 * @author edmaputra
 * @since 0.14.0
 */
public record EnvironmentAttributes(
		String clientIp,
		Instant timestamp,
		String userAgent,
		DevicePosture devicePosture,
		Map<String, Object> attributes) {

	public EnvironmentAttributes {
		timestamp = timestamp == null ? Instant.now() : timestamp;
		devicePosture = devicePosture == null ? DevicePosture.unknown() : devicePosture;
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
		clientIp = clientIp != null ? clientIp.trim() : null;
		userAgent = userAgent != null ? userAgent.trim() : null;
	}

	public static EnvironmentAttributes current(String clientIp, String userAgent) {
		return new EnvironmentAttributes(clientIp, Instant.now(), userAgent, DevicePosture.unknown(), Map.of());
	}
}
