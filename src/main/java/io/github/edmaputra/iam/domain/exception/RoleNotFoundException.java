package io.github.edmaputra.iam.domain.exception;

import io.github.edmaputra.iam.domain.model.RoleId;

/**
 * Thrown when a role cannot be found in the catalog.
 *
 * @author edmaputra
 */
public class RoleNotFoundException extends RuntimeException {

	/**
	 * Constructs the exception with a role ID.
	 *
	 * @param roleId the missing role ID
	 */
	public RoleNotFoundException(RoleId roleId) {
		super("Role not found with id: " + roleId);
	}

	/**
	 * Constructs the exception with a role code.
	 *
	 * @param code the missing role code
	 */
	public RoleNotFoundException(String code) {
		super("Role not found with code: " + code);
	}
}
