package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.policy.AttributeCondition;
import io.github.edmaputra.iam.domain.policy.AttributeOperator;
import io.github.edmaputra.iam.domain.policy.AttributeSource;
import io.github.edmaputra.iam.domain.policy.DevicePostureConstraint;
import io.github.edmaputra.iam.domain.policy.NetworkCidrConstraint;
import io.github.edmaputra.iam.domain.policy.PolicyConstraint;
import io.github.edmaputra.iam.domain.policy.TimeWindowConstraint;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PolicyConstraintJsonConverter}.
 *
 * @author edmaputra
 * @since 0.14.0
 */
class PolicyConstraintJsonConverterTest {

	private PolicyConstraintJsonConverter converter;

	@BeforeEach
	void setUp() {
		converter = new PolicyConstraintJsonConverter(new ObjectMapper());
	}

	@Test
	@DisplayName("Should serialize and deserialize time window constraint correctly")
	void shouldRoundTripTimeWindowConstraint() {
		TimeWindowConstraint original = new TimeWindowConstraint(
				LocalTime.of(8, 30),
				LocalTime.of(17, 30),
				Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
				ZoneId.of("America/New_York")
		);

		String json = converter.toJson(List.of(original));
		assertThat(json).contains("TIME_WINDOW");
		assertThat(json).contains("08:30");
		assertThat(json).contains("17:30");

		List<PolicyConstraint> deserialized = converter.fromJson(json);
		assertThat(deserialized).hasSize(1);
		assertThat(deserialized.getFirst()).isInstanceOf(TimeWindowConstraint.class);

		TimeWindowConstraint result = (TimeWindowConstraint) deserialized.getFirst();
		assertThat(result.startTime()).isEqualTo(LocalTime.of(8, 30));
		assertThat(result.endTime()).isEqualTo(LocalTime.of(17, 30));
		assertThat(result.daysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
		assertThat(result.zoneId()).isEqualTo(ZoneId.of("America/New_York"));
	}

	@Test
	@DisplayName("Should serialize and deserialize network CIDR constraint correctly")
	void shouldRoundTripNetworkCidrConstraint() {
		NetworkCidrConstraint original = new NetworkCidrConstraint(
				Set.of("10.0.0.0/8", "192.168.1.0/24"),
				Set.of("10.100.0.0/16")
		);

		String json = converter.toJson(List.of(original));
		assertThat(json).contains("NETWORK_CIDR");

		List<PolicyConstraint> deserialized = converter.fromJson(json);
		assertThat(deserialized).hasSize(1);
		assertThat(deserialized.getFirst()).isInstanceOf(NetworkCidrConstraint.class);

		NetworkCidrConstraint result = (NetworkCidrConstraint) deserialized.getFirst();
		assertThat(result.allowedCidrs()).containsExactlyInAnyOrder("10.0.0.0/8", "192.168.1.0/24");
		assertThat(result.deniedCidrs()).containsExactly("10.100.0.0/16");
	}

	@Test
	@DisplayName("Should serialize and deserialize device posture constraint correctly")
	void shouldRoundTripDevicePostureConstraint() {
		DevicePostureConstraint original = new DevicePostureConstraint(
				true,
				"HIGH",
				Set.of("MACOS", "LINUX")
		);

		String json = converter.toJson(List.of(original));
		assertThat(json).contains("DEVICE_POSTURE");

		List<PolicyConstraint> deserialized = converter.fromJson(json);
		assertThat(deserialized).hasSize(1);
		assertThat(deserialized.getFirst()).isInstanceOf(DevicePostureConstraint.class);

		DevicePostureConstraint result = (DevicePostureConstraint) deserialized.getFirst();
		assertThat(result.requireManaged()).isTrue();
		assertThat(result.minTrustLevel()).isEqualTo("HIGH");
		assertThat(result.allowedPlatforms()).containsExactlyInAnyOrder("MACOS", "LINUX");
	}

	@Test
	@DisplayName("Should serialize and deserialize attribute condition correctly")
	void shouldRoundTripAttributeCondition() {
		AttributeCondition original = new AttributeCondition(
				AttributeSource.SUBJECT,
				"department",
				AttributeOperator.EQUALS,
				null,
				AttributeSource.RESOURCE,
				"department"
		);

		String json = converter.toJson(List.of(original));
		assertThat(json).contains("ATTRIBUTE_CONDITION");

		List<PolicyConstraint> deserialized = converter.fromJson(json);
		assertThat(deserialized).hasSize(1);
		assertThat(deserialized.getFirst()).isInstanceOf(AttributeCondition.class);

		AttributeCondition result = (AttributeCondition) deserialized.getFirst();
		assertThat(result.source()).isEqualTo(AttributeSource.SUBJECT);
		assertThat(result.attributeName()).isEqualTo("department");
		assertThat(result.operator()).isEqualTo(AttributeOperator.EQUALS);
		assertThat(result.targetSource()).isEqualTo(AttributeSource.RESOURCE);
		assertThat(result.targetAttributeName()).isEqualTo("department");
	}
}
