package io.github.edmaputra.iam.adapter.rest;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.exception.ScopeNodeNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;

/**
 * Controller advice handling IAM domain exceptions and mapping them to RFC 9457 {@link ProblemDetail} responses.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestControllerAdvice(basePackages = "io.github.edmaputra.iam")
public class IamExceptionHandler {

	/**
	 * Handles authentication exceptions and returns HTTP 401 Unauthorized.
	 *
	 * @param ex the authentication exception
	 * @return RFC 9457 problem detail with HTTP 401
	 */
	@ExceptionHandler(AuthenticationException.class)
	public ProblemDetail handleAuthentication(AuthenticationException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	/**
	 * Handles access denied exceptions and returns HTTP 403 Forbidden.
	 *
	 * @param ex the access denied exception
	 * @return RFC 9457 problem detail with HTTP 403
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	/**
	 * Handles not found exceptions and returns HTTP 404 Not Found.
	 *
	 * @param ex the entity not found exception
	 * @return RFC 9457 problem detail with HTTP 404
	 */
	@ExceptionHandler({
			UserNotFoundException.class,
			RoleNotFoundException.class,
			ScopeNodeNotFoundException.class,
			GroupNotFoundException.class
	})
	public ProblemDetail handleNotFound(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/**
	 * Handles validation exceptions and returns RFC 9457 HTTP 422 Unprocessable Entity with structured field errors.
	 *
	 * @param ex      the method argument validation exception
	 * @param request the current HTTP servlet request
	 * @return RFC 9457 problem detail with HTTP 422 and validation errors
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<Map<String, Object>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> Map.<String, Object>of(
						"field", fe.getField(),
						"message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "",
						"rejectedValue", fe.getRejectedValue() != null ? fe.getRejectedValue() : "null"
				))
				.toList();

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"Request contains %d validation errors.".formatted(fieldErrors.size())
		);
		problem.setTitle("Validation Failed");
		problem.setType(URI.create("https://api.edmaputra.github.io/problems/validation-error"));
		if (request != null) {
			problem.setInstance(URI.create(request.getRequestURI()));
		}
		problem.setProperty("errors", fieldErrors);
		return problem;
	}

	/**
	 * Handles illegal argument or state exceptions and returns HTTP 400 Bad Request.
	 *
	 * @param ex the bad request exception
	 * @return RFC 9457 problem detail with HTTP 400
	 */
	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ProblemDetail handleBadRequest(Exception ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}
}
