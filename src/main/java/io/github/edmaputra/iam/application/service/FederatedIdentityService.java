package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;

/**
 * Domain service managing federated identity linkages, Just-In-Time (JIT) user provisioning, and external group claim synchronization.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class FederatedIdentityService {

	private final UserRepository userRepository;
	private final UserIdentityRepository userIdentityRepository;
	private final GroupRepository groupRepository;
	private final UserGroupMembershipRepository userGroupMembershipRepository;

	/**
	 * Constructs the federated identity service with required domain repositories.
	 *
	 * @param userRepository               the user repository
	 * @param userIdentityRepository       the user identity linkage repository
	 * @param groupRepository              the group repository
	 * @param userGroupMembershipRepository the group membership repository
	 */
	public FederatedIdentityService(
			UserRepository userRepository,
			UserIdentityRepository userIdentityRepository,
			GroupRepository groupRepository,
			UserGroupMembershipRepository userGroupMembershipRepository) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.userIdentityRepository = Objects.requireNonNull(userIdentityRepository, "UserIdentityRepository must not be null.");
		this.groupRepository = Objects.requireNonNull(groupRepository, "GroupRepository must not be null.");
		this.userGroupMembershipRepository = Objects.requireNonNull(userGroupMembershipRepository, "UserGroupMembershipRepository must not be null.");
	}

	/**
	 * Links an external identity to an existing account, or creates a new user account via JIT provisioning.
	 * Synchronizes external group claims with tenant groups matching {@code external_idp_group_name}.
	 *
	 * @param providerType       the external identity provider type
	 * @param externalSubjectId  the external subject ID from the IdP
	 * @param email              the user's email address
	 * @param fullName           the user's full name
	 * @param issuerUrl          optional issuer URL
	 * @param externalGroupNames optional list of external group claim names
	 * @param tenantId           target tenant ID (optional)
	 * @return the authenticated identity
	 * @throws AuthenticationException if the user account is suspended or deactivated
	 */
	public AuthenticatedIdentity linkOrProvisionUser(
			ProviderType providerType,
			String externalSubjectId,
			String email,
			String fullName,
			String issuerUrl,
			List<String> externalGroupNames,
			TenantId tenantId) {

		Objects.requireNonNull(providerType, "ProviderType must not be null.");
		Objects.requireNonNull(externalSubjectId, "ExternalSubjectId must not be null.");
		Objects.requireNonNull(email, "Email must not be null.");

		// 1. Find existing linked identity
		Optional<UserIdentity> existingIdentity = userIdentityRepository
				.findByProviderTypeAndExternalSubjectId(providerType, externalSubjectId);

		User user;
		if (existingIdentity.isPresent()) {
			user = userRepository.findById(existingIdentity.get().getUserId())
					.orElseThrow(() -> new AuthenticationException("Linked user account not found."));
		}
		else {
			// 2. Look up user by email or JIT provision
			user = userRepository.findByEmail(email)
					.orElseGet(() -> {
						String name = (fullName != null && !fullName.isBlank()) ? fullName : email;
						User newUser = User.createExternal(email, name, false);
						return userRepository.save(newUser);
					});

			// 3. Link new identity
			UserIdentity newIdentity = UserIdentity.create(user.getId(), providerType, externalSubjectId, issuerUrl);
			userIdentityRepository.save(newIdentity);
		}

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		// 4. Synchronize external group claims to tenant groups
		if (tenantId != null && externalGroupNames != null && !externalGroupNames.isEmpty()) {
			for (String extGroup : externalGroupNames) {
				if (extGroup != null && !extGroup.isBlank()) {
					groupRepository.findByTenantIdAndExternalIdpGroupName(tenantId, extGroup.trim()).ifPresent(group -> {
						if (!userGroupMembershipRepository.existsByGroupIdAndUserId(group.getId(), user.getId())) {
							userGroupMembershipRepository.save(UserGroupMembership.of(group.getId(), user.getId()));
						}
					});
				}
			}
		}

		return new AuthenticatedIdentity(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.isPlatformSuperAdmin(),
				providerType);
	}
}
