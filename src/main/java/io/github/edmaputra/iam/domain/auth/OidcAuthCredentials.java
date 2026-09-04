package io.github.edmaputra.iam.domain.auth;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Encapsulates OpenID Connect (OIDC) authentication claims and credentials.
 *
 * @param idToken        optional raw ID token string
 * @param email          the user's email address extracted from IdP claims
 * @param subject        the external subject ID uniquely identifying the user in the IdP
 * @param fullName       the user's full name extracted from IdP claims
 * @param issuerUrl      the OIDC issuer URL
 * @param externalGroups list of external group or role claim strings from the IdP
 * @param tenantId       the target tenant ID if authenticating into a specific tenant
 * @author edmaputra
 */
public record OidcAuthCredentials(
		String idToken,
		String email,
		String subject,
		String fullName,
		String issuerUrl,
		List<String> externalGroups,
		TenantId tenantId) implements AuthCredentials {

	public OidcAuthCredentials {
		Objects.requireNonNull(email, "Email must not be null.");
		Objects.requireNonNull(subject, "Subject must not be null.");
		if (email.isBlank()) {
			throw new IllegalArgumentException("Email must not be blank.");
		}
		if (subject.isBlank()) {
			throw new IllegalArgumentException("Subject must not be blank.");
		}
		if (fullName == null || fullName.isBlank()) {
			fullName = email;
		}
		externalGroups = externalGroups == null ? List.of() : List.copyOf(externalGroups);
	}

	@Override
	public AuthCredentialType credentialType() {
		return AuthCredentialType.OIDC_TOKEN;
	}

	/**
	 * Returns the optional raw ID token.
	 *
	 * @return optional ID token string
	 */
	public Optional<String> optionalIdToken() {
		return Optional.ofNullable(idToken);
	}

	/**
	 * Returns the optional OIDC issuer URL.
	 *
	 * @return optional issuer URL string
	 */
	public Optional<String> optionalIssuerUrl() {
		return Optional.ofNullable(issuerUrl);
	}

	/**
	 * Returns the optional target tenant ID.
	 *
	 * @return optional target {@link TenantId}
	 */
	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}
}
