package io.github.edmaputra.iam.domain.repository;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;

/**
 * Domain repository port for managing direct {@link UserRoleAssignment} bindings.
 *
 * @author edmaputra
 */
public interface UserRoleAssignmentRepository {

	/**
	 * Finds a user role assignment by unique identifier.
	 *
	 * @param id the assignment ID
	 * @return optional containing the assignment if found
	 */
	Optional<UserRoleAssignment> findById(UserRoleAssignmentId id);

	/**
	 * Finds all role assignments for a user across all tenants/scopes.
	 *
	 * @param userId the user ID
	 * @return list of user role assignments
	 */
	List<UserRoleAssignment> findAllByUserId(UserId userId);

	/**
	 * Finds all role assignments for a user within a specific tenant.
	 *
	 * @param userId   the user ID
	 * @param tenantId the tenant ID
	 * @return list of role assignments
	 */
	List<UserRoleAssignment> findAllByUserIdAndTenantId(UserId userId, TenantId tenantId);

	/**
	 * Saves a user role assignment.
	 *
	 * @param assignment the assignment to persist
	 * @return the persisted assignment
	 */
	UserRoleAssignment save(UserRoleAssignment assignment);

	/**
	 * Deletes a user role assignment by unique identifier.
	 *
	 * @param id the assignment ID
	 */
	void delete(UserRoleAssignmentId id);
}
