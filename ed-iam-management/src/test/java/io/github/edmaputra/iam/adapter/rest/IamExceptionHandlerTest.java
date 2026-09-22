package io.github.edmaputra.iam.adapter.rest;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IamExceptionHandler}.
 *
 * @author edmaputra
 * @since 0.1.0
 */
class IamExceptionHandlerTest {

	private IamExceptionHandler handler;

	@BeforeEach
	void setUp() {
		handler = new IamExceptionHandler();
	}

	@Test
	void shouldReturn401WhenAuthenticationException() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

		ProblemDetail problem = handler.handleAuthentication(new AuthenticationException("Invalid credentials"), request);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(problem.getTitle()).isEqualTo("Authentication Failed");
		assertThat(problem.getType()).isEqualTo(URI.create("https://api.edmaputra.github.io/problems/authentication-failed"));
		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/auth/login"));
		assertThat(problem.getDetail()).isEqualTo("Invalid credentials");
	}

	@Test
	void shouldReturn403WhenAccessDeniedException() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/v1/users");

		ProblemDetail problem = handler.handleAccessDenied(new AccessDeniedException("Forbidden action"), request);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(problem.getTitle()).isEqualTo("Access Denied");
		assertThat(problem.getType()).isEqualTo(URI.create("https://api.edmaputra.github.io/problems/access-denied"));
		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/users"));
		assertThat(problem.getDetail()).isEqualTo("Forbidden action");
	}

	@Test
	void shouldReturn404WhenNotFoundException() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/v1/users/by-email");

		ProblemDetail problem = handler.handleNotFound(new UserNotFoundException("test@example.com"), request);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
		assertThat(problem.getType()).isEqualTo(URI.create("https://api.edmaputra.github.io/problems/resource-not-found"));
		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/users/by-email"));
		assertThat(problem.getDetail()).isEqualTo("User not found with email: test@example.com");
	}

	@Test
	void shouldReturn400WhenIllegalArgumentException() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/v1/auth/switch-tenant");

		ProblemDetail problem = handler.handleBadRequest(new IllegalArgumentException("Illegal param"), request);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getType()).isEqualTo(URI.create("https://api.edmaputra.github.io/problems/bad-request"));
		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/auth/switch-tenant"));
		assertThat(problem.getDetail()).isEqualTo("Illegal param");
	}

	@Test
	void shouldReturn422WithStructuredErrorsWhenValidationException() throws NoSuchMethodException {
		record SampleDto(String email) {}
		SampleDto target = new SampleDto("invalid-email");
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "sampleDto");
		bindingResult.addError(new FieldError("sampleDto", "email", "invalid-email", false, null, null, "must be a well-formed email address"));

		Method method = IamExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", Object.class);
		MethodParameter parameter = new MethodParameter(method, 0);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/v1/users");

		ProblemDetail problem = handler.handleValidation(ex, request);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
		assertThat(problem.getTitle()).isEqualTo("Validation Failed");
		assertThat(problem.getType()).isEqualTo(URI.create("https://api.edmaputra.github.io/problems/validation-error"));
		assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/users"));
		assertThat(problem.getDetail()).isEqualTo("Request contains 1 validation errors.");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> errors = (List<Map<String, Object>>) problem.getProperties().get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.getFirst().get("field")).isEqualTo("email");
		assertThat(errors.getFirst().get("message")).isEqualTo("must be a well-formed email address");
		assertThat(errors.getFirst().get("rejectedValue")).isEqualTo("invalid-email");
	}

	@SuppressWarnings("unused")
	private void sampleMethod(Object param) {
	}
}
