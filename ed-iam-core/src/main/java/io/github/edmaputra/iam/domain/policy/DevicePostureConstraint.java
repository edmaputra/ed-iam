package io.github.edmaputra.iam.domain.policy;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contextual constraint enforcing client device security posture, management status,
 * minimum trust levels, and permitted operating systems.
 *
 * @param requireManaged whether the accessing client must be an MDM or enterprise-managed device
 * @param minTrustLevel minimum acceptable trust level (e.g., "HIGH", "MEDIUM", "LOW")
 * @param allowedPlatforms permitted operating systems or platforms (e.g., "LINUX", "MACOS", "WINDOWS")
 * @author edmaputra
 * @since 0.14.0
 */
public record DevicePostureConstraint(
		boolean requireManaged,
		String minTrustLevel,
		Set<String> allowedPlatforms) implements PolicyConstraint {

	public static final String TYPE = "DEVICE_POSTURE";

	public DevicePostureConstraint {
		minTrustLevel = (minTrustLevel == null || minTrustLevel.isBlank()) ? null : minTrustLevel.trim().toUpperCase(Locale.ROOT);
		allowedPlatforms = allowedPlatforms == null ? Set.of() : allowedPlatforms.stream()
				.filter(p -> p != null && !p.isBlank())
				.map(p -> p.trim().toUpperCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}

	public DevicePostureConstraint(Boolean requireManaged, String minTrustLevel, Set<String> allowedPlatforms) {
		this(Boolean.TRUE.equals(requireManaged), minTrustLevel, allowedPlatforms);
	}

	public DevicePostureConstraint(Boolean requireManaged, Set<String> allowedTrustLevels, Set<String> allowedPlatforms) {
		this(Boolean.TRUE.equals(requireManaged),
				allowedTrustLevels != null && !allowedTrustLevels.isEmpty() ? allowedTrustLevels.iterator().next() : null,
				allowedPlatforms);
	}

	public Set<String> allowedTrustLevels() {
		return minTrustLevel != null ? Set.of(minTrustLevel) : Set.of();
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public boolean evaluate(PolicyEvaluationContext context) {
		DevicePosture posture = context.environment().devicePosture();
		if (posture == null) {
			return !requireManaged && minTrustLevel == null && allowedPlatforms.isEmpty();
		}

		if (requireManaged && !posture.managed()) {
			return false;
		}

		if (minTrustLevel != null) {
			int currentScore = trustScore(posture.trustLevel());
			int requiredScore = trustScore(minTrustLevel);
			if (currentScore < requiredScore) {
				return false;
			}
		}

		if (!allowedPlatforms.isEmpty()) {
			String platform = posture.platform();
			if (platform == null || !allowedPlatforms.contains(platform.toUpperCase(Locale.ROOT))) {
				return false;
			}
		}

		return true;
	}

	private static int trustScore(String level) {
		if (level == null) {
			return 0;
		}
		return switch (level.toUpperCase(Locale.ROOT)) {
			case "HIGH" -> 3;
			case "MEDIUM" -> 2;
			case "LOW" -> 1;
			default -> 0;
		};
	}
}
