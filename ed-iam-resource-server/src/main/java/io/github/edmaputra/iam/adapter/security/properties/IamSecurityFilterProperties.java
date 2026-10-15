package io.github.edmaputra.iam.adapter.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties controlling servlet registration, activation,
 * and failure pass-through behavior for {@link io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter}.
 *
 * @param enabled            whether the JWT authentication filter bean is instantiated (default {@code true})
 * @param registerServlet    whether the filter is auto-registered into the servlet container (default {@code true})
 * @param failurePassthrough whether to allow unparseable/invalid tokens to pass to downstream security filters (default {@code false})
 * @author edmaputra
 * @since 0.12.0
 */
@ConfigurationProperties(prefix = "ed-iam.security.filter")
public record IamSecurityFilterProperties(
		Boolean enabled,
		Boolean registerServlet,
		Boolean failurePassthrough) {

	public static final boolean DEFAULT_ENABLED = true;
	public static final boolean DEFAULT_REGISTER_SERVLET = true;
	public static final boolean DEFAULT_FAILURE_PASSTHROUGH = false;

	public IamSecurityFilterProperties {
		if (enabled == null) {
			enabled = DEFAULT_ENABLED;
		}
		if (registerServlet == null) {
			registerServlet = DEFAULT_REGISTER_SERVLET;
		}
		if (failurePassthrough == null) {
			failurePassthrough = DEFAULT_FAILURE_PASSTHROUGH;
		}
	}

	/**
	 * Creates a default configuration with filter enabled, servlet registration enabled,
	 * and failure pass-through disabled.
	 *
	 * @return default filter properties instance
	 */
	public static IamSecurityFilterProperties defaultProperties() {
		return new IamSecurityFilterProperties(DEFAULT_ENABLED, DEFAULT_REGISTER_SERVLET, DEFAULT_FAILURE_PASSTHROUGH);
	}
}
