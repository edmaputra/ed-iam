package io.github.edmaputra.iam.adapter.security.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Magic Link passwordless authentication.
 *
 * @param enabled               whether magic link authentication is enabled (default true)
 * @param expirationSeconds     lifetime of an issued magic link token in seconds (default 900s = 15m)
 * @param baseUrl               base application URL used to construct the verification link
 * @param allowedRedirectHosts  list of allowed target hostnames for post-verification redirect
 * @author edmaputra
 * @since 0.7.0
 */
@ConfigurationProperties(prefix = "iam.auth.magic-link")
public record MagicLinkProperties(
		Boolean enabled,
		long expirationSeconds,
		String baseUrl,
		List<String> allowedRedirectHosts) {

	/** Default enabled status. */
	public static final boolean DEFAULT_ENABLED = true;
	/** Default token expiration duration in seconds (15 minutes). */
	public static final long DEFAULT_EXPIRATION_SECONDS = 900L;
	/** Default base application URL. */
	public static final String DEFAULT_BASE_URL = "http://localhost:8080";


	public MagicLinkProperties {
		if (enabled == null) {
			enabled = DEFAULT_ENABLED;
		}
		if (expirationSeconds <= 0) {
			expirationSeconds = DEFAULT_EXPIRATION_SECONDS;
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = DEFAULT_BASE_URL;
		}
		if (allowedRedirectHosts == null) {
			allowedRedirectHosts = List.of();
		}
		else {
			allowedRedirectHosts = List.copyOf(allowedRedirectHosts);
		}
	}

	/**
	 * Creates default magic link configuration properties.
	 *
	 * @return default {@link MagicLinkProperties}
	 */
	public static MagicLinkProperties defaultProperties() {
		return new MagicLinkProperties(DEFAULT_ENABLED, DEFAULT_EXPIRATION_SECONDS, DEFAULT_BASE_URL, List.of());
	}
}
