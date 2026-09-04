package io.github.edmaputra.iam.domain.event;

/**
 * Standard event type constants for IAM domain events.
 *
 * @author edmaputra
 */
public final class IamEventTypes {

	private IamEventTypes() {}

	// User Events
	public static final String USER_CREATED = "USER_CREATED";
	public static final String USER_UPDATED = "USER_UPDATED";
	public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
	public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
	public static final String USER_DEACTIVATED = "USER_DEACTIVATED";

	// Role Events
	public static final String ROLE_CREATED = "ROLE_CREATED";
	public static final String ROLE_UPDATED = "ROLE_UPDATED";
	public static final String ROLE_DELETED = "ROLE_DELETED";

	// Scope Events
	public static final String SCOPE_NODE_CREATED = "SCOPE_NODE_CREATED";
	public static final String SCOPE_NODE_UPDATED = "SCOPE_NODE_UPDATED";
	public static final String SCOPE_NODE_MOVED = "SCOPE_NODE_MOVED";
	public static final String SCOPE_NODE_DELETED = "SCOPE_NODE_DELETED";

	// Group Events
	public static final String GROUP_CREATED = "GROUP_CREATED";
	public static final String GROUP_UPDATED = "GROUP_UPDATED";
	public static final String GROUP_DELETED = "GROUP_DELETED";
	public static final String GROUP_MEMBERSHIP_ADDED = "GROUP_MEMBERSHIP_ADDED";
	public static final String GROUP_MEMBERSHIP_REMOVED = "GROUP_MEMBERSHIP_REMOVED";

	// Assignment Events
	public static final String ROLE_ASSIGNMENT_CREATED = "ROLE_ASSIGNMENT_CREATED";
	public static final String ROLE_ASSIGNMENT_REVOKED = "ROLE_ASSIGNMENT_REVOKED";
}
