package io.github.edmaputra.iam.domain.exception;

import io.github.edmaputra.iam.domain.model.GroupId;

/**
 * Thrown when a user group cannot be found.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class GroupNotFoundException extends RuntimeException {

	/**
	 * Constructs the exception with a group ID.
	 *
	 * @param groupId the missing group ID
	 */
	public GroupNotFoundException(GroupId groupId) {
		super("Group not found with id: " + groupId);
	}

	/**
	 * Constructs the exception with a group code.
	 *
	 * @param code the missing group code
	 */
	public GroupNotFoundException(String code) {
		super("Group not found with code: " + code);
	}
}
