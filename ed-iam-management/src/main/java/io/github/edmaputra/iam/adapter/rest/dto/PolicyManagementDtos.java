package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;

import io.github.edmaputra.iam.domain.policy.AttributeCondition;
import io.github.edmaputra.iam.domain.policy.AttributeOperator;
import io.github.edmaputra.iam.domain.policy.AttributeSource;
import io.github.edmaputra.iam.domain.policy.DecisionEffect;
import io.github.edmaputra.iam.domain.policy.DevicePostureConstraint;
import io.github.edmaputra.iam.domain.policy.NetworkCidrConstraint;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyConstraint;
import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.policy.TimeWindowConstraint;

/**
 * Request and response DTOs for policy administrative management endpoints.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public final class PolicyManagementDtos {

	private PolicyManagementDtos() {}

	public record PolicyTargetDto(
			Set<String> actions,
			Set<String> resourceTypes,
			Set<String> roles) {

		public PolicyTarget toDomain() {
			return new PolicyTarget(actions, resourceTypes, roles);
		}

		public static PolicyTargetDto fromDomain(PolicyTarget target) {
			if (target == null) {
				return new PolicyTargetDto(Set.of("*"), Set.of("*"), Set.of());
			}
			return new PolicyTargetDto(target.actions(), target.resourceTypes(), target.roles());
		}
	}

	public record PolicyConstraintDto(
			String type,
			String startTime,
			String endTime,
			Set<String> daysOfWeek,
			String zoneId,
			Set<String> allowedCidrs,
			Set<String> deniedCidrs,
			Boolean requireManaged,
			Set<String> allowedTrustLevels,
			Set<String> allowedPlatforms,
			String source,
			String attributeName,
			String operator,
			Object targetValue,
			String targetSource,
			String targetAttributeName) {

		public PolicyConstraint toDomain() {
			if (type == null) {
				throw new IllegalArgumentException("Constraint type must not be null.");
			}
			return switch (type.toUpperCase()) {
				case "TIME_WINDOW" -> new TimeWindowConstraint(
						startTime != null && !startTime.isBlank() ? LocalTime.parse(startTime) : null,
						endTime != null && !endTime.isBlank() ? LocalTime.parse(endTime) : null,
						daysOfWeek != null ? daysOfWeek.stream().map(String::toUpperCase).map(DayOfWeek::valueOf).collect(Collectors.toSet()) : Set.of(),
						zoneId != null && !zoneId.isBlank() ? ZoneId.of(zoneId) : null
				);
				case "NETWORK_CIDR" -> new NetworkCidrConstraint(allowedCidrs, deniedCidrs);
				case "DEVICE_POSTURE" -> new DevicePostureConstraint(requireManaged, allowedTrustLevels, allowedPlatforms);
				case "ATTRIBUTE_CONDITION" -> new AttributeCondition(
						source != null ? AttributeSource.valueOf(source.toUpperCase()) : AttributeSource.SUBJECT,
						attributeName,
						operator != null ? AttributeOperator.valueOf(operator.toUpperCase()) : AttributeOperator.EQUALS,
						targetValue,
						targetSource != null && !targetSource.isBlank() ? AttributeSource.valueOf(targetSource.toUpperCase()) : null,
						targetAttributeName
				);
				default -> throw new IllegalArgumentException("Unsupported constraint type: " + type);
			};
		}

		public static PolicyConstraintDto fromDomain(PolicyConstraint constraint) {
			if (constraint instanceof TimeWindowConstraint tw) {
				return new PolicyConstraintDto(
						"TIME_WINDOW",
						tw.startTime() != null ? tw.startTime().toString() : null,
						tw.endTime() != null ? tw.endTime().toString() : null,
						tw.daysOfWeek() != null ? tw.daysOfWeek().stream().map(Enum::name).collect(Collectors.toSet()) : Set.of(),
						tw.zoneId() != null ? tw.zoneId().getId() : null,
						null, null, null, null, null, null, null, null, null, null, null
				);
			} else if (constraint instanceof NetworkCidrConstraint nc) {
				return new PolicyConstraintDto(
						"NETWORK_CIDR",
						null, null, null, null,
						nc.allowedCidrs(), nc.deniedCidrs(),
						null, null, null, null, null, null, null, null, null
				);
			} else if (constraint instanceof DevicePostureConstraint dp) {
				return new PolicyConstraintDto(
						"DEVICE_POSTURE",
						null, null, null, null, null, null,
						dp.requireManaged(), dp.allowedTrustLevels(), dp.allowedPlatforms(),
						null, null, null, null, null, null
				);
			} else if (constraint instanceof AttributeCondition ac) {
				return new PolicyConstraintDto(
						"ATTRIBUTE_CONDITION",
						null, null, null, null, null, null, null, null, null,
						ac.source() != null ? ac.source().name() : null,
						ac.attributeName(),
						ac.operator() != null ? ac.operator().name() : null,
						ac.targetValue(),
						ac.targetSource() != null ? ac.targetSource().name() : null,
						ac.targetAttributeName()
				);
			}
			throw new IllegalArgumentException("Unknown constraint implementation: " + constraint.getClass().getName());
		}
	}

	public record CreatePolicyRequest(
			UUID tenantId,

			@NotBlank(message = "Policy name must not be blank.")
			String name,

			String description,
			PolicyEffect effect,
			Integer priority,
			Boolean enabled,
			PolicyTargetDto target,
			List<PolicyConstraintDto> constraints) {

		public PolicyEffect resolveEffect() {
			return effect != null ? effect : PolicyEffect.PERMIT;
		}

		public int resolvePriority() {
			return priority != null ? priority : 0;
		}

		public boolean resolveEnabled() {
			return enabled == null || enabled;
		}
	}

	public record UpdatePolicyRequest(
			@NotBlank(message = "Policy name must not be blank.")
			String name,

			String description,
			PolicyEffect effect,
			Integer priority,
			Boolean enabled,
			PolicyTargetDto target,
			List<PolicyConstraintDto> constraints) {

		public PolicyEffect resolveEffect() {
			return effect != null ? effect : PolicyEffect.PERMIT;
		}

		public int resolvePriority() {
			return priority != null ? priority : 0;
		}

		public boolean resolveEnabled() {
			return enabled == null || enabled;
		}
	}

	public record PolicyResponse(
			UUID id,
			UUID tenantId,
			String name,
			String description,
			String effect,
			int priority,
			boolean enabled,
			PolicyTargetDto target,
			List<PolicyConstraintDto> constraints,
			Instant createdAt,
			Instant updatedAt) {

		public static PolicyResponse fromDomain(Policy policy) {
			return new PolicyResponse(
					policy.id().value(),
					policy.tenantId() != null ? policy.tenantId().value() : null,
					policy.name(),
					policy.description(),
					policy.effect().name(),
					policy.priority(),
					policy.enabled(),
					PolicyTargetDto.fromDomain(policy.target()),
					policy.constraints() != null
							? policy.constraints().stream().map(PolicyConstraintDto::fromDomain).toList()
							: List.of(),
					policy.createdAt(),
					policy.updatedAt()
			);
		}
	}

	public record PolicyEvaluationRequestDto(
			@NotBlank(message = "Action must not be blank.")
			String action,

			@NotBlank(message = "Resource type must not be blank.")
			String resourceType,

			String resourceId,
			UUID resourceTenantId,
			UUID resourceOwnerId,
			Map<String, Object> resourceAttributes,
			Map<String, Object> environmentAttributes) {}

	public record PolicyEvaluationResponseDto(
			boolean permitted,
			String effect,
			String reason,
			List<String> matchedPolicyIds,
			Instant evaluatedAt) {

		public static PolicyEvaluationResponseDto fromDomain(PolicyDecision decision) {
			return new PolicyEvaluationResponseDto(
					decision.isPermitted(),
					decision.effect().name(),
					decision.reason(),
					decision.matchedPolicyIds(),
					decision.evaluatedAt()
			);
		}
	}
}
