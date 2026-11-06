package io.github.edmaputra.iam.adapter.security.interceptor;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;

import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.application.port.in.EvaluatePolicyUseCase;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.policy.DecisionEffect;
import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.annotation.RequirePolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequirePolicyInterceptor}.
 *
 * @author edmaputra
 * @since 0.14.0
 */
@ExtendWith(MockitoExtension.class)
class RequirePolicyInterceptorTest {

	@Mock
	private CurrentActorProvider currentActorProvider;

	@Mock
	private ObjectProvider<EvaluatePolicyUseCase> evaluatorProvider;

	@Mock
	private EvaluatePolicyUseCase policyEvaluator;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	private RequirePolicyInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new RequirePolicyInterceptor(currentActorProvider, evaluatorProvider, SecurityAuditRecorder.noop());
	}

	@Test
	@DisplayName("Should pass non-HandlerMethod objects")
	void shouldPassNonHandlerMethods() {
		boolean result = interceptor.preHandle(request, response, new Object());
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should pass handler methods without @RequirePolicy annotation")
	void shouldPassUnannotatedMethods() throws Exception {
		Method method = SamplePolicyController.class.getMethod("unannotatedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SamplePolicyController(), method);

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should pass when no policy evaluator is registered")
	void shouldPassWhenNoEvaluatorRegistered() throws Exception {
		Method method = SamplePolicyController.class.getMethod("policyProtectedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SamplePolicyController(), method);

		when(evaluatorProvider.getIfAvailable()).thenReturn(null);

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should throw AuthenticationException when actor is absent")
	void shouldThrowWhenActorAbsent() throws Exception {
		Method method = SamplePolicyController.class.getMethod("policyProtectedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SamplePolicyController(), method);

		when(evaluatorProvider.getIfAvailable()).thenReturn(policyEvaluator);
		when(currentActorProvider.currentActor()).thenReturn(Optional.empty());
		when(request.getRequestURI()).thenReturn("/api/v1/records/1");

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Authentication is required");
	}

	@Test
	@DisplayName("Should grant access unconditionally to platform superadmin")
	void shouldGrantAccessToPlatformSuperadmin() throws Exception {
		Method method = SamplePolicyController.class.getMethod("policyProtectedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SamplePolicyController(), method);

		when(evaluatorProvider.getIfAvailable()).thenReturn(policyEvaluator);

		CurrentActor superAdmin = new SecurityContextCurrentActor(
				UUID.randomUUID(), "admin@ed-iam.io", null, true, true,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(superAdmin));

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should grant access when policy permits")
	void shouldGrantAccessWhenPolicyPermits() throws Exception {
		Method method = SamplePolicyController.class.getMethod("policyProtectedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SamplePolicyController(), method);

		when(evaluatorProvider.getIfAvailable()).thenReturn(policyEvaluator);

		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "doctor@hospital.org", UUID.randomUUID(), false, false,
				Set.of("DOCTOR"), Set.of("CLINICAL"), Set.of(), Set.of(), Set.of());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));
		when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");
		when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
		when(policyEvaluator.evaluate(any())).thenReturn(PolicyDecision.permit("Allowed by policy"));

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should throw AccessDeniedException when policy denies")
	void shouldThrowAccessDeniedWhenPolicyDenies() throws Exception {
		Method method = SamplePolicyController.class.getMethod("policyProtectedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SamplePolicyController(), method);

		when(evaluatorProvider.getIfAvailable()).thenReturn(policyEvaluator);

		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "doctor@hospital.org", UUID.randomUUID(), false, false,
				Set.of("DOCTOR"), Set.of("CLINICAL"), Set.of(), Set.of(), Set.of());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));
		when(request.getRequestURI()).thenReturn("/api/v1/records/1");
		when(policyEvaluator.evaluate(any())).thenReturn(PolicyDecision.deny("Denied by security policy"));

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Denied by security policy");
	}

	static class SamplePolicyController {
		public void unannotatedEndpoint() {
		}

		@RequirePolicy(action = "read", resourceType = "medical_record")
		public void policyProtectedEndpoint() {
		}
	}
}
