package io.github.edmaputra.iam.adapter.security.evaluator;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.github.edmaputra.iam.application.port.in.EvaluatePolicyUseCase;
import io.github.edmaputra.iam.domain.policy.DevicePosture;
import io.github.edmaputra.iam.domain.policy.EnvironmentAttributes;
import io.github.edmaputra.iam.domain.policy.PolicyEvaluationContext;
import io.github.edmaputra.iam.domain.policy.ResourceAttributes;
import io.github.edmaputra.iam.domain.policy.SubjectAttributes;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

/**
 * Spring Security SpEL evaluator bean named {@code "iam"}.
 *
 * <p>Enables declarative method-level expressions, such as:
 * <pre>{@code
 * @PreAuthorize("@iam.hasPermission('iam:user:create')")
 * @PreAuthorize("@iam.hasPermission('CLINICAL_WRITE') and @iam.canAccessScope(#departmentId)")
 * @PreAuthorize("@iam.evaluatePolicy('export', 'patient_record')")
 * }</pre>
 *
 * @author edmaputra
 * @since 0.1.0
 */
public class IamSecurityEvaluator {

	private final CurrentActorProvider currentActorProvider;
	private final ObjectProvider<EvaluatePolicyUseCase> policyEvaluatorProvider;

	public IamSecurityEvaluator(CurrentActorProvider currentActorProvider) {
		this(currentActorProvider, null);
	}

	public IamSecurityEvaluator(
			CurrentActorProvider currentActorProvider,
			ObjectProvider<EvaluatePolicyUseCase> policyEvaluatorProvider) {
		this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "CurrentActorProvider must not be null.");
		this.policyEvaluatorProvider = policyEvaluatorProvider;
	}

	/**
	 * Checks if the authenticated actor has the specified permission.
	 * Returns {@code true} if the actor is a platform superadmin.
	 *
	 * @param permission the required permission code
	 * @return true if granted, false otherwise
	 */
	public boolean hasPermission(String permission) {
		if (permission == null || permission.isBlank()) {
			return false;
		}
		return currentActor().map(actor -> actor.hasPermission(permission)).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has at least one of the specified permissions.
	 *
	 * @param permissions the permission codes to check
	 * @return true if at least one permission is granted, false otherwise
	 */
	public boolean hasAnyPermission(String... permissions) {
		if (permissions == null || permissions.length == 0) {
			return false;
		}
		return currentActor().map(actor -> {
			if (actor.isPlatformSuperAdmin()) {
				return true;
			}
			for (String permission : permissions) {
				if (actor.hasPermission(permission)) {
					return true;
				}
			}
			return false;
		}).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has all of the specified permissions.
	 *
	 * @param permissions the permission codes to check
	 * @return true if all permissions are granted, false otherwise
	 */
	public boolean hasAllPermissions(String... permissions) {
		if (permissions == null || permissions.length == 0) {
			return false;
		}
		return currentActor().map(actor -> {
			if (actor.isPlatformSuperAdmin()) {
				return true;
			}
			for (String permission : permissions) {
				if (!actor.hasPermission(permission)) {
					return false;
				}
			}
			return true;
		}).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has access to the target scope hierarchy node.
	 *
	 * @param scopeNodeId target scope node UUID
	 * @return true if scope is accessible, false otherwise
	 */
	public boolean canAccessScope(UUID scopeNodeId) {
		if (scopeNodeId == null) {
			return false;
		}
		return currentActor().map(actor -> actor.canAccessScope(scopeNodeId)).orElse(false);
	}

	/**
	 * Checks if the authenticated actor is a platform superadmin.
	 *
	 * @return true if platform superadmin, false otherwise
	 */
	public boolean isPlatformSuperAdmin() {
		return currentActor().map(CurrentActor::isPlatformSuperAdmin).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has tenant-wide access.
	 *
	 * @return true if tenant-wide or platform superadmin, false otherwise
	 */
	public boolean isTenantWide() {
		return currentActor().map(CurrentActor::isTenantWide).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has the specified role code.
	 *
	 * @param role the role code
	 * @return true if assigned, false otherwise
	 */
	public boolean hasRole(String role) {
		if (role == null || role.isBlank()) {
			return false;
		}
		return currentActor().map(actor -> actor.roles() != null && actor.roles().contains(role)).orElse(false);
	}

	/**
	 * Checks if the authenticated actor belongs to the specified group code.
	 *
	 * @param group the group code
	 * @return true if member, false otherwise
	 */
	public boolean isInGroup(String group) {
		if (group == null || group.isBlank()) {
			return false;
		}
		return currentActor().map(actor -> actor.groups() != null && actor.groups().contains(group)).orElse(false);
	}

	/**
	 * Evaluates dynamic ABAC policies for the current actor executing the given action against a resource type.
	 *
	 * @param action the action to evaluate
	 * @param resourceType the target resource type
	 * @return true if permitted, false otherwise
	 * @since 0.14.0
	 */
	public boolean evaluatePolicy(String action, String resourceType) {
		return evaluatePolicy(action, resourceType, Map.of());
	}

	/**
	 * Evaluates dynamic ABAC policies for the current actor executing the given action against a resource type
	 * with contextual resource attributes.
	 *
	 * @param action the action to evaluate
	 * @param resourceType the target resource type
	 * @param resourceAttributes attributes of the resource
	 * @return true if permitted, false otherwise
	 * @since 0.14.0
	 */
	public boolean evaluatePolicy(String action, String resourceType, Map<String, Object> resourceAttributes) {
		if (action == null || action.isBlank() || resourceType == null || resourceType.isBlank()) {
			return false;
		}

		Optional<CurrentActor> actorOpt = currentActor();
		if (actorOpt.isEmpty()) {
			return false;
		}

		CurrentActor actor = actorOpt.get();
		if (actor.isPlatformSuperAdmin()) {
			return true;
		}

		EvaluatePolicyUseCase evaluator = policyEvaluatorProvider != null ? policyEvaluatorProvider.getIfAvailable() : null;
		if (evaluator == null) {
			return false;
		}

		EnvironmentAttributes env = resolveEnvironmentAttributes();
		SubjectAttributes subject = new SubjectAttributes(
				actor.userId(),
				actor.tenantId(),
				actor.email(),
				actor.roles(),
				actor.groups(),
				actor.permissions(),
				Map.of()
		);
		ResourceAttributes resource = new ResourceAttributes(
				null,
				resourceType,
				null,
				null,
				resourceAttributes != null ? resourceAttributes : Map.of()
		);

		PolicyEvaluationContext context = new PolicyEvaluationContext(subject, action, resource, env);
		return evaluator.evaluate(context).isPermitted();
	}

	/**
	 * Alias for {@link #evaluatePolicy(String, String)}.
	 *
	 * @param action the action to evaluate
	 * @param resourceType the target resource type
	 * @return true if permitted, false otherwise
	 * @since 0.14.0
	 */
	public boolean isAllowed(String action, String resourceType) {
		return evaluatePolicy(action, resourceType, Map.of());
	}

	/**
	 * Alias for {@link #evaluatePolicy(String, String, Map)}.
	 *
	 * @param action the action to evaluate
	 * @param resourceType the target resource type
	 * @param resourceAttributes attributes of the resource
	 * @return true if permitted, false otherwise
	 * @since 0.14.0
	 */
	public boolean isAllowed(String action, String resourceType, Map<String, Object> resourceAttributes) {
		return evaluatePolicy(action, resourceType, resourceAttributes);
	}

	private EnvironmentAttributes resolveEnvironmentAttributes() {
		RequestAttributes reqAttr = RequestContextHolder.getRequestAttributes();
		if (reqAttr instanceof ServletRequestAttributes servletAttrs) {
			HttpServletRequest request = servletAttrs.getRequest();
			String clientIp = extractClientIp(request);
			String userAgent = request.getHeader("User-Agent");
			DevicePosture posture = extractDevicePosture(request);
			return new EnvironmentAttributes(clientIp, Instant.now(), userAgent, posture, Map.of());
		}
		return new EnvironmentAttributes(null, Instant.now(), null, DevicePosture.unknown(), Map.of());
	}

	private static String extractClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
		}
		return request.getRemoteAddr();
	}

	private static DevicePosture extractDevicePosture(HttpServletRequest request) {
		String managedHeader = request.getHeader("X-Device-Managed");
		boolean managed = managedHeader != null && Boolean.parseBoolean(managedHeader.trim());
		String trustLevel = request.getHeader("X-Device-Trust-Level");
		String platform = request.getHeader("X-Device-Platform");
		return new DevicePosture(managed, trustLevel, platform, Map.of());
	}

	private Optional<CurrentActor> currentActor() {
		return currentActorProvider.currentActor();
	}
}
