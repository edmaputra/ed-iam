package io.github.edmaputra.iam.domain.exception;

import io.github.edmaputra.iam.domain.model.PermissionId;

/**
 * Thrown when a permission cannot be found in the catalog.
 *
 * @author edmaputra
 * @since 0.10.0
 */
public class PermissionNotFoundException extends RuntimeException {

	/**
	 * Constructs the exception with a permission ID.
	 *
	 * @param permissionId the missing permission ID
	 */
	public PermissionNotFoundException(PermissionId permissionId) {
		super("Permission not found with id: " + permissionId);
	}

	/**
	 * Constructs the exception with a permission code.
	 *
	 * @param code the missing permission code
	 */
	public PermissionNotFoundException(String code) {
		super("Permission not found with code: " + code);
	}
}
