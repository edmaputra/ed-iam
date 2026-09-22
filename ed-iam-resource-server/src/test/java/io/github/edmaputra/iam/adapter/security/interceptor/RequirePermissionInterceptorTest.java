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
import org.springframework.web.method.HandlerMethod;

import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.annotation.Logical;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequirePermissionInterceptor}.
 *
 * @author edmaputra
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class RequirePermissionInterceptorTest {

	@Mock
	private CurrentActorProvider currentActorProvider;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	private RequirePermissionInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new RequirePermissionInterceptor(currentActorProvider);
	}

	@Test
	@DisplayName("Should pass non-HandlerMethod objects")
	void shouldPassNonHandlerMethods() {
		boolean result = interceptor.preHandle(request, response, new Object());
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should pass handler methods without @RequirePermission annotation")
	void shouldPassUnannotatedMethods() throws Exception {
		Method method = SampleController.class.getMethod("unannotatedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should throw AuthenticationException when actor is absent on protected endpoint")
	void shouldThrowAuthenticationExceptionWhenActorAbsent() throws Exception {
		Method method = SampleController.class.getMethod("singlePermissionEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);

		when(currentActorProvider.currentActor()).thenReturn(Optional.empty());

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Authentication is required");
	}

	@Test
	@DisplayName("Should grant access unconditionally to platform superadmin")
	void shouldGrantAccessToPlatformSuperadmin() throws Exception {
		Method method = SampleController.class.getMethod("singlePermissionEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);

		CurrentActor superAdmin = new SecurityContextCurrentActor(
				UUID.randomUUID(), "admin@ed-iam.io", null, true, true,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of()
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(superAdmin));

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should grant access when actor possesses required permission (AND)")
	void shouldGrantAccessWhenPermissionPresent() throws Exception {
		Method method = SampleController.class.getMethod("singlePermissionEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);

		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "user@ed-iam.io", UUID.randomUUID(), false, false,
				Set.of(), Set.of(), Set.of("iam:user:create"), Set.of(), Set.of()
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should throw AccessDeniedException when actor lacks required permission (AND)")
	void shouldThrowAccessDeniedWhenPermissionMissing() throws Exception {
		Method method = SampleController.class.getMethod("singlePermissionEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);

		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "user@ed-iam.io", UUID.randomUUID(), false, false,
				Set.of(), Set.of(), Set.of("other:perm"), Set.of(), Set.of()
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("lacks required permission(s)");
	}

	@Test
	@DisplayName("Should grant access when actor possesses one of multiple permissions with Logical.OR")
	void shouldGrantAccessWithLogicalOr() throws Exception {
		Method method = SampleController.class.getMethod("orPermissionsEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);

		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "user@ed-iam.io", UUID.randomUUID(), false, false,
				Set.of(), Set.of(), Set.of("perm:two"), Set.of(), Set.of()
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Should throw AccessDeniedException when actor possesses none of multiple permissions with Logical.OR")
	void shouldDenyAccessWithLogicalOrWhenNoneMatch() throws Exception {
		Method method = SampleController.class.getMethod("orPermissionsEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);

		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "user@ed-iam.io", UUID.randomUUID(), false, false,
				Set.of(), Set.of(), Set.of("perm:three"), Set.of(), Set.of()
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("lacks required permission(s)");
	}

	@Test
	@DisplayName("Should inherit class-level @RequirePermission when method has no annotation")
	void shouldInheritClassLevelAnnotation() throws Exception {
		Method method = ClassAnnotatedController.class.getMethod("inheritedEndpoint");
		HandlerMethod handlerMethod = new HandlerMethod(new ClassAnnotatedController(), method);

		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "user@ed-iam.io", UUID.randomUUID(), false, false,
				Set.of(), Set.of(), Set.of("class:permission"), Set.of(), Set.of()
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		boolean result = interceptor.preHandle(request, response, handlerMethod);
		assertThat(result).isTrue();
	}

	static class SampleController {

		public void unannotatedEndpoint() {
		}

		@RequirePermission("iam:user:create")
		public void singlePermissionEndpoint() {
		}

		@RequirePermission(value = {"perm:one", "perm:two"}, logical = Logical.OR)
		public void orPermissionsEndpoint() {
		}
	}

	@RequirePermission("class:permission")
	static class ClassAnnotatedController {

		public void inheritedEndpoint() {
		}
	}
}
