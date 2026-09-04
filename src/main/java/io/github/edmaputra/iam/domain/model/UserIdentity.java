package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import lombok.Getter;

/**
 * Pure domain entity representing a federated identity provider linkage for a local user.
 *
 * @author edmaputra
 */
@Getter
public class UserIdentity {

	private final UserIdentityId id;
	private final UserId userId;
	private final ProviderType providerType;
	private final String externalSubjectId;
	private final String issuerUrl;
	private final Instant createdAt;

	/**
	 * Canonical constructor for reconstructing user identity domain models.
	 *
	 * @param id                the unique identity ID
	 * @param userId            the internal user ID
	 * @param providerType      the IdP provider type
	 * @param externalSubjectId the external subject ID from the IdP
	 * @param issuerUrl         optional issuer URL
	 * @param createdAt         creation timestamp
	 */
	public UserIdentity(
			UserIdentityId id,
			UserId userId,
			ProviderType providerType,
			String externalSubjectId,
			String issuerUrl,
			Instant createdAt) {
		this.id = Objects.requireNonNull(id, "UserIdentity ID must not be null.");
		this.userId = Objects.requireNonNull(userId, "UserId must not be null.");
		this.providerType = Objects.requireNonNull(providerType, "ProviderType must not be null.");
		this.externalSubjectId = Objects.requireNonNull(externalSubjectId, "ExternalSubjectId must not be null.");
		this.issuerUrl = issuerUrl;
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
	}

	/**
	 * Factory method creating a new federated user identity linkage.
	 *
	 * @param userId            the internal user ID
	 * @param providerType      the IdP provider type
	 * @param externalSubjectId the external subject ID
	 * @param issuerUrl         optional issuer URL
	 * @return new {@link UserIdentity}
	 */
	public static UserIdentity create(
			UserId userId,
			ProviderType providerType,
			String externalSubjectId,
			String issuerUrl) {
		return new UserIdentity(
				UserIdentityId.generate(),
				userId,
				providerType,
				externalSubjectId,
				issuerUrl,
				Instant.now());
	}

	/**
	 * Returns the optional issuer URL.
	 *
	 * @return optional issuer URL string
	 */
	public Optional<String> optionalIssuerUrl() {
		return Optional.ofNullable(issuerUrl);
	}
}
