package io.github.edmaputra.iam.domain.policy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Contextual Policy Constraints Unit Tests")
class ContextualConstraintsTest {

	private PolicyEvaluationContext createContext(Instant timestamp, String clientIp, DevicePosture posture) {
		return new PolicyEvaluationContext(
				new SubjectAttributes(UUID.randomUUID(), UUID.randomUUID(), "doctor@hospital.org", Set.of("DOCTOR"), Set.of(), Set.of(), Map.of()),
				"read",
				ResourceAttributes.ofType("patient-record"),
				new EnvironmentAttributes(clientIp, timestamp, "Mozilla/5.0", posture, Map.of())
		);
	}

	@Nested
	@DisplayName("TimeWindowConstraint Tests")
	class TimeWindowConstraintTests {

		@Test
		@DisplayName("Should permit during active business hours on permitted day")
		void shouldPermitDuringBusinessHours() {
			TimeWindowConstraint constraint = TimeWindowConstraint.businessHours(
					LocalTime.of(8, 0), LocalTime.of(18, 0), "UTC"
			);

			// Monday 10:30 UTC
			Instant mondayMidday = ZonedDateTime.of(2026, 10, 26, 10, 30, 0, 0, ZoneId.of("UTC")).toInstant();
			PolicyEvaluationContext context = createContext(mondayMidday, "10.0.0.1", null);

			assertThat(constraint.evaluate(context)).isTrue();
		}

		@Test
		@DisplayName("Should deny outside business hours or on weekends")
		void shouldDenyOutsideBusinessHoursOrWeekend() {
			TimeWindowConstraint constraint = TimeWindowConstraint.businessHours(
					LocalTime.of(8, 0), LocalTime.of(18, 0), "UTC"
			);

			// Monday 19:30 UTC (after hours)
			Instant mondayNight = ZonedDateTime.of(2026, 10, 26, 19, 30, 0, 0, ZoneId.of("UTC")).toInstant();
			assertThat(constraint.evaluate(createContext(mondayNight, "10.0.0.1", null))).isFalse();

			// Sunday 12:00 UTC (weekend)
			Instant sundayNoon = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant();
			assertThat(constraint.evaluate(createContext(sundayNoon, "10.0.0.1", null))).isFalse();
		}

		@Test
		@DisplayName("Should handle overnight shifts spanning midnight")
		void shouldHandleOvernightShifts() {
			// Night shift: 22:00 to 06:00
			TimeWindowConstraint nightShift = new TimeWindowConstraint(
					LocalTime.of(22, 0), LocalTime.of(6, 0), Set.of(), "UTC"
			);

			Instant at2300 = ZonedDateTime.of(2026, 10, 26, 23, 0, 0, 0, ZoneId.of("UTC")).toInstant();
			Instant at0300 = ZonedDateTime.of(2026, 10, 27, 3, 0, 0, 0, ZoneId.of("UTC")).toInstant();
			Instant at1400 = ZonedDateTime.of(2026, 10, 27, 14, 0, 0, 0, ZoneId.of("UTC")).toInstant();

			assertThat(nightShift.evaluate(createContext(at2300, "10.0.0.1", null))).isTrue();
			assertThat(nightShift.evaluate(createContext(at0300, "10.0.0.1", null))).isTrue();
			assertThat(nightShift.evaluate(createContext(at1400, "10.0.0.1", null))).isFalse();
		}
	}

	@Nested
	@DisplayName("NetworkCidrConstraint Tests")
	class NetworkCidrConstraintTests {

		@Test
		@DisplayName("Should permit when client IP is in allowed CIDR subnet")
		void shouldPermitAllowedCidr() {
			NetworkCidrConstraint constraint = NetworkCidrConstraint.allowOnly(Set.of("192.168.1.0/24", "10.0.0.0/8"));

			assertThat(constraint.evaluate(createContext(Instant.now(), "192.168.1.45", null))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "10.200.1.5", null))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "172.16.0.1", null))).isFalse();
		}

		@Test
		@DisplayName("Should deny when client IP matches denied CIDR even if allowed")
		void shouldDenyBlockedSubnet() {
			NetworkCidrConstraint constraint = new NetworkCidrConstraint(
					Set.of("10.0.0.0/8"),
					Set.of("10.100.0.0/16") // Denied subnet within 10.0.0.0/8
			);

			assertThat(constraint.evaluate(createContext(Instant.now(), "10.1.0.5", null))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "10.100.5.5", null))).isFalse();
		}

		@Test
		@DisplayName("Should reject blank or null client IP")
		void shouldRejectMissingIp() {
			NetworkCidrConstraint constraint = NetworkCidrConstraint.allowOnly(Set.of("10.0.0.0/8"));
			assertThat(constraint.evaluate(createContext(Instant.now(), null, null))).isFalse();
			assertThat(constraint.evaluate(createContext(Instant.now(), "", null))).isFalse();
		}
	}

	@Nested
	@DisplayName("DevicePostureConstraint Tests")
	class DevicePostureConstraintTests {

		@Test
		@DisplayName("Should enforce managed device requirement")
		void shouldEnforceManagedDevice() {
			DevicePostureConstraint constraint = new DevicePostureConstraint(true, null, Set.of());

			DevicePosture managed = new DevicePosture(true, "HIGH", "LINUX", Map.of());
			DevicePosture unmanaged = new DevicePosture(false, "HIGH", "LINUX", Map.of());

			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", managed))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", unmanaged))).isFalse();
		}

		@Test
		@DisplayName("Should enforce minimum trust level score")
		void shouldEnforceMinTrustLevel() {
			DevicePostureConstraint constraint = new DevicePostureConstraint(false, "MEDIUM", Set.of());

			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", new DevicePosture(false, "HIGH", "MACOS", Map.of())))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", new DevicePosture(false, "MEDIUM", "MACOS", Map.of())))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", new DevicePosture(false, "LOW", "MACOS", Map.of())))).isFalse();
			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", new DevicePosture(false, "UNKNOWN", "MACOS", Map.of())))).isFalse();
		}

		@Test
		@DisplayName("Should enforce allowed platforms")
		void shouldEnforceAllowedPlatforms() {
			DevicePostureConstraint constraint = new DevicePostureConstraint(false, null, Set.of("LINUX", "MACOS"));

			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", new DevicePosture(false, "LOW", "LINUX", Map.of())))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", new DevicePosture(false, "LOW", "MACOS", Map.of())))).isTrue();
			assertThat(constraint.evaluate(createContext(Instant.now(), "127.0.0.1", new DevicePosture(false, "LOW", "WINDOWS", Map.of())))).isFalse();
		}
	}
}
