package io.github.edmaputra.iam.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Internal utility class for extracting HTTP request metadata such as client IP addresses and user agents.
 *
 * @author edmaputra
 * @since 0.6.0
 */
final class HttpRequestUtils {

	private HttpRequestUtils() {
	}

	/**
	 * Extracts the client IP address from the request, checking standard proxy forwarding headers first.
	 *
	 * @param request the HTTP servlet request
	 * @return client IP string, or null if request is null
	 */
	static String extractClientIp(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isBlank()) {
			return xForwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	/**
	 * Extracts the User-Agent header from the request.
	 *
	 * @param request the HTTP servlet request
	 * @return User-Agent string, or null
	 */
	static String extractUserAgent(HttpServletRequest request) {
		return request != null ? request.getHeader(HttpHeaders.USER_AGENT) : null;
	}
}
