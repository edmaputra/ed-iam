package io.github.edmaputra.iam.adapter.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Multi-Factor Authentication (MFA / TOTP).
 *
 * @param issuer the TOTP issuer name displayed in authenticator applications (default "ed-iam")
 * @param endpoints configuration options for built-in REST endpoints
 * @author edmaputra
 * @since 0.11.0
 */
@ConfigurationProperties(prefix = "iam.auth.mfa")
public record MfaProperties(
		String issuer,
		Endpoints endpoints) {

	/** Default TOTP issuer name. */
	public static final String DEFAULT_ISSUER = "ed-iam";

	public MfaProperties {
		if (issuer == null || issuer.isBlank()) {
			issuer = DEFAULT_ISSUER;
		}
		if (endpoints == null) {
			endpoints = new Endpoints(false);
		}
	}

	/**
	 * Creates default MFA configuration properties with endpoints disabled by default.
	 *
	 * @return default {@link MfaProperties}
	 */
	public static MfaProperties defaultProperties() {
		return new MfaProperties(DEFAULT_ISSUER, new Endpoints(false));
	}

	/**
	 * Configuration options for built-in MFA REST endpoints.
	 *
	 * @param enabled whether the built-in MFA REST controller is registered (default false)
	 * @author edmaputra
	 * @since 0.11.0
	 */
	public record Endpoints(Boolean enabled) {

		public Endpoints {
			if (enabled == null) {
				enabled = false;
			}
		}
	}
}
