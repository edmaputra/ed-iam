package io.github.edmaputra.iam.adapter.security.interceptor;

import java.util.Arrays;
import java.util.List;
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
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.annotation.Logical;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;

/**
 * Spring MVC {@link HandlerInterceptor} enforcing declarative endpoint-level permissions
 * specified via {@link RequirePermission}.
 *
 * @author edmaputra
 * @since 0.1.0
 */
public class RequirePermissionInterceptor implements HandlerInterceptor {

	private final CurrentActorProvider currentActorProvider;
	private final SecurityAuditRecorder auditRecorder;

	public RequirePermissionInterceptor(
			CurrentActorProvider currentActorProvider,
			SecurityAuditRecorder auditRecorder) {
		this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "CurrentActorProvider must not be null.");
		this.auditRecorder = auditRecorder != null ? auditRecorder : SecurityAuditRecorder.noop();
	}

	public RequirePermissionInterceptor(
			CurrentActorProvider currentActorProvider,
			ObjectProvider<IamTelemetry> telemetryProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		this(currentActorProvider, new SecurityAuditRecorder(
				telemetryProvider != null ? telemetryProvider.getIfAvailable() : null,
				eventPublisherProvider != null ? eventPublisherProvider.getIfAvailable() : null));
	}

	public RequirePermissionInterceptor(CurrentActorProvider currentActorProvider) {
		this(currentActorProvider, SecurityAuditRecorder.noop());
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		RequirePermission annotation = findRequirePermissionAnnotation(handlerMethod);
		if (annotation == null) {
			return true;
		}

		String[] permissions = annotation.value();
		String permsStr = permissions != null ? Arrays.toString(permissions) : "[]";

		Optional<CurrentActor> actorOpt = currentActorProvider.currentActor();
		if (actorOpt.isEmpty()) {
			auditRecorder.recordAccessDenied(
					"unauthenticated",
					permsStr,
					null,
					"anonymous",
					request.getRequestURI(),
					Map.of("requiredPermissions", permissions != null ? Arrays.asList(permissions) : List.of())
			);
			throw new AuthenticationException("Authentication is required to access this resource.");
		}

		CurrentActor actor = actorOpt.get();
		if (actor.isPlatformSuperAdmin()) {
			return true;
		}

		if (permissions == null || permissions.length == 0) {
			return true;
		}

		boolean authorized = evaluatePermissions(actor, permissions, annotation.logical());
		if (!authorized) {
			String actorIdStr = actor.userId() != null ? actor.userId().toString() : "anonymous";
			auditRecorder.recordAccessDenied(
					"missing_permission",
					permsStr,
					actor.tenantId(),
					actorIdStr,
					request.getRequestURI(),
					Map.of("requiredPermissions", Arrays.asList(permissions))
			);
			throw new AccessDeniedException("Actor [" + actor.userId() + "] lacks required permission(s): "
					+ permsStr);
		}

		return true;
	}

	private RequirePermission findRequirePermissionAnnotation(HandlerMethod handlerMethod) {
		RequirePermission methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
				handlerMethod.getMethod(), RequirePermission.class);
		if (methodAnnotation != null) {
			return methodAnnotation;
		}
		return AnnotatedElementUtils.findMergedAnnotation(
				handlerMethod.getBeanType(), RequirePermission.class);
	}

	private boolean evaluatePermissions(CurrentActor actor, String[] permissions, Logical logical) {
		if (logical == Logical.OR) {
			for (String permission : permissions) {
				if (actor.hasPermission(permission)) {
					return true;
				}
			}
			return false;
		}

		for (String permission : permissions) {
			if (!actor.hasPermission(permission)) {
				return false;
			}
		}
		return true;
	}
}
