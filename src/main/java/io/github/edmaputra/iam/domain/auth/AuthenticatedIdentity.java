package io.github.edmaputra.iam.domain.auth;

import java.util.Objects;

import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Result of successful credential verification by an {@link io.github.edmaputra.iam.application.port.out.AuthenticationProvider}.
 *
 * @param userId              the authenticated internal user ID
 * @param email               the user's email address
 * @param fullName            the user's full name
 * @param platformSuperAdmin  flag indicating if the user is a platform superadmin
 * @param providerType        the provider type that authenticated this identity
 * @author edmaputra
 * @since 1.0.0
 */
public record AuthenticatedIdentity(
		UserId userId,
		String email,
		String fullName,
		boolean platformSuperAdmin,
		ProviderType providerType) {

	public AuthenticatedIdentity {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(email, "Email must not be null.");
		Objects.requireNonNull(fullName, "FullName must not be null.");
		Objects.requireNonNull(providerType, "ProviderType must not be null.");
	}
}
