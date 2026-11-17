package io.github.edmaputra.iam.adapter.security.interceptor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetry;
import io.github.edmaputra.iam.application.port.in.EvaluatePolicyUseCase;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.policy.DevicePosture;
import io.github.edmaputra.iam.domain.policy.EnvironmentAttributes;
import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.policy.PolicyEvaluationContext;
import io.github.edmaputra.iam.domain.policy.ResourceAttributes;
import io.github.edmaputra.iam.domain.policy.SubjectAttributes;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.annotation.RequirePolicy;

/**
 * Spring MVC {@link HandlerInterceptor} enforcing declarative Attribute-Based Access Control
 * specified via {@link RequirePolicy}.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public class RequirePolicyInterceptor implements HandlerInterceptor {

	private final CurrentActorProvider currentActorProvider;
	private final ObjectProvider<EvaluatePolicyUseCase> policyEvaluatorProvider;
	private final SecurityAuditRecorder auditRecorder;

	public RequirePolicyInterceptor(
			CurrentActorProvider currentActorProvider,
			ObjectProvider<EvaluatePolicyUseCase> policyEvaluatorProvider,
			SecurityAuditRecorder auditRecorder) {
		this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "CurrentActorProvider must not be null.");
		this.policyEvaluatorProvider = policyEvaluatorProvider;
		this.auditRecorder = auditRecorder != null ? auditRecorder : SecurityAuditRecorder.noop();
	}

	public RequirePolicyInterceptor(
			CurrentActorProvider currentActorProvider,
			ObjectProvider<EvaluatePolicyUseCase> policyEvaluatorProvider,
			ObjectProvider<IamTelemetry> telemetryProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		this(currentActorProvider, policyEvaluatorProvider, new SecurityAuditRecorder(
				telemetryProvider != null ? telemetryProvider.getIfAvailable() : null,
				eventPublisherProvider != null ? eventPublisherProvider.getIfAvailable() : null));
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		RequirePolicy annotation = findRequirePolicyAnnotation(handlerMethod);
		if (annotation == null) {
			return true;
		}

		EvaluatePolicyUseCase evaluator = policyEvaluatorProvider != null ? policyEvaluatorProvider.getIfAvailable() : null;
		if (evaluator == null) {
			// No ABAC engine registered, bypass interceptor
			return true;
		}

		Optional<CurrentActor> actorOpt = currentActorProvider.currentActor();
		if (actorOpt.isEmpty()) {
			auditRecorder.recordAccessDenied(
					"unauthenticated",
					annotation.action(),
					null,
					"anonymous",
					request.getRequestURI(),
					Map.of("action", annotation.action(), "resourceType", annotation.resourceType())
			);
			throw new AuthenticationException("Authentication is required to access this resource.");
		}

		CurrentActor actor = actorOpt.get();
		if (actor.isPlatformSuperAdmin()) {
			return true;
		}

		String clientIp = extractClientIp(request);
		String userAgent = request.getHeader("User-Agent");
		DevicePosture posture = extractDevicePosture(request);

		SubjectAttributes subject = new SubjectAttributes(
				actor.userId(),
				actor.tenantId(),
				actor.email(),
				actor.roles(),
				actor.groups(),
				actor.permissions(),
				Map.of()
		);

		ResourceAttributes resource = ResourceAttributes.ofType(annotation.resourceType());
		EnvironmentAttributes env = new EnvironmentAttributes(clientIp, Instant.now(), userAgent, posture, Map.of());

		PolicyEvaluationContext context = new PolicyEvaluationContext(subject, annotation.action(), resource, env);
		PolicyDecision decision = evaluator.evaluate(context);

		if (!decision.isPermitted()) {
			String actorIdStr = actor.userId() != null ? actor.userId().toString() : "anonymous";
			auditRecorder.recordAccessDenied(
					"policy_denied",
					annotation.action(),
					actor.tenantId(),
					actorIdStr,
					request.getRequestURI(),
					Map.of(
							"action", annotation.action(),
							"resourceType", annotation.resourceType(),
							"decision", decision.effect().name(),
							"reason", decision.reason()
					)
			);
			throw new AccessDeniedException("Access denied by ABAC policy: " + decision.reason());
		}

		return true;
	}

	private RequirePolicy findRequirePolicyAnnotation(HandlerMethod handlerMethod) {
		RequirePolicy methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
				handlerMethod.getMethod(), RequirePolicy.class);
		if (methodAnnotation != null) {
			return methodAnnotation;
		}
		return AnnotatedElementUtils.findMergedAnnotation(
				handlerMethod.getBeanType(), RequirePolicy.class);
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
}
