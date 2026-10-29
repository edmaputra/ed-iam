package io.github.edmaputra.iam.domain.policy;

import java.util.Map;

/**
 * Value object representing client device posture and security telemetry.
 *
 * @param managed whether the client device is corporate or MDM managed
 * @param trustLevel trust assessment score or level (e.g., "HIGH", "MEDIUM", "LOW", "UNKNOWN")
 * @param platform operating system or platform (e.g., "LINUX", "MACOS", "WINDOWS", "IOS", "ANDROID", "UNKNOWN")
 * @param attributes additional device posture attributes
 * @author edmaputra
 * @since 0.14.0
 */
public record DevicePosture(
		boolean managed,
		String trustLevel,
		String platform,
		Map<String, Object> attributes) {

	public DevicePosture {
		trustLevel = (trustLevel == null || trustLevel.isBlank()) ? "UNKNOWN" : trustLevel.trim().toUpperCase();
		platform = (platform == null || platform.isBlank()) ? "UNKNOWN" : platform.trim().toUpperCase();
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}

	public static DevicePosture unknown() {
		return new DevicePosture(false, "UNKNOWN", "UNKNOWN", Map.of());
	}
}
