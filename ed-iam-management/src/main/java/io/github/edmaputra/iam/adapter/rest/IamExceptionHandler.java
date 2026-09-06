package io.github.edmaputra.iam.adapter.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.exception.ScopeNodeNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;

/**
 * Controller advice handling IAM domain exceptions and mapping them to RFC 7807 {@link ProblemDetail} responses.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@RestControllerAdvice(basePackages = "io.github.edmaputra.iam")
public class IamExceptionHandler {

	/**
	 * Handles authentication exceptions and returns HTTP 401 Unauthorized.
	 *
	 * @param ex the authentication exception
	 * @return RFC 7807 problem detail with HTTP 401
	 */
	@ExceptionHandler(AuthenticationException.class)
	public ProblemDetail handleAuthentication(AuthenticationException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	/**
	 * Handles access denied exceptions and returns HTTP 403 Forbidden.
	 *
	 * @param ex the access denied exception
	 * @return RFC 7807 problem detail with HTTP 403
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	/**
	 * Handles not found exceptions and returns HTTP 404 Not Found.
	 *
	 * @param ex the entity not found exception
	 * @return RFC 7807 problem detail with HTTP 404
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
	 * Handles illegal argument or state exceptions and returns HTTP 400 Bad Request.
	 *
	 * @param ex the bad request exception
	 * @return RFC 7807 problem detail with HTTP 400
	 */
	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ProblemDetail handleBadRequest(Exception ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}
}
