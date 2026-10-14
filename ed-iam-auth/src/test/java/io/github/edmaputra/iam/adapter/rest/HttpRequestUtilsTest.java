package io.github.edmaputra.iam.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpRequestUtils}.
 *
 * @author edmaputra
 * @since 0.11.0
 */
class HttpRequestUtilsTest {

	@Test
	@DisplayName("Should extract client IP from X-Forwarded-For header when present")
	void shouldExtractClientIpFromForwardedHeader() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");

		String ip = HttpRequestUtils.extractClientIp(request);

		assertThat(ip).isEqualTo("192.168.1.100");
	}

	@Test
	@DisplayName("Should fallback to getRemoteAddr when X-Forwarded-For is missing")
	void shouldFallbackToRemoteAddr() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("X-Forwarded-For")).thenReturn(null);
		when(request.getRemoteAddr()).thenReturn("127.0.0.1");

		String ip = HttpRequestUtils.extractClientIp(request);

		assertThat(ip).isEqualTo("127.0.0.1");
	}

	@Test
	@DisplayName("Should return null client IP when request is null")
	void shouldReturnNullWhenRequestIsNull() {
		assertThat(HttpRequestUtils.extractClientIp(null)).isNull();
		assertThat(HttpRequestUtils.extractUserAgent(null)).isNull();
	}

	@Test
	@DisplayName("Should extract User-Agent header from request")
	void shouldExtractUserAgent() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("Mozilla/5.0 TestBrowser");

		String userAgent = HttpRequestUtils.extractUserAgent(request);

		assertThat(userAgent).isEqualTo("Mozilla/5.0 TestBrowser");
	}
}
