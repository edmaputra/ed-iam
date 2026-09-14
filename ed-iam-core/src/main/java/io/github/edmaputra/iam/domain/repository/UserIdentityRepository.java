package io.github.edmaputra.iam.domain.repository;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.model.UserIdentityId;

/**
 * Domain repository port for managing federated {@link UserIdentity} linkages.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface UserIdentityRepository {

	/**
	 * Finds a user identity by unique record ID.
	 *
	 * @param id the user identity ID
	 * @return optional containing the user identity if found
	 */
	Optional<UserIdentity> findById(UserIdentityId id);

	/**
	 * Finds a linked identity by external identity provider type and subject ID.
	 *
	 * @param providerType      the IdP provider type
	 * @param externalSubjectId the external subject ID from the IdP
	 * @return optional containing the linked identity if found
	 */
	Optional<UserIdentity> findByProviderTypeAndExternalSubjectId(ProviderType providerType, String externalSubjectId);

	/**
	 * Finds all linked identities for a specific internal user.
	 *
	 * @param userId the internal user ID
	 * @return list of linked identities
	 */
	List<UserIdentity> findAllByUserId(UserId userId);

	/**
	 * Saves or updates a user identity linkage.
	 *
	 * @param identity the identity record to save
	 * @return the saved identity
	 */
	UserIdentity save(UserIdentity identity);

	/**
	 * Deletes a user identity linkage by ID.
	 *
	 * @param id the user identity ID
	 */
	void delete(UserIdentityId id);
}
