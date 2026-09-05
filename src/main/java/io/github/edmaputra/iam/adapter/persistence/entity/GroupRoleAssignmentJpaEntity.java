package io.github.edmaputra.iam.adapter.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a group-level role-to-scope assignment in the {@code iam_group_role_assignment} table.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@Entity
@Table(name = "iam_group_role_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupRoleAssignmentJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "role_id", nullable = false)
	private UUID roleId;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "scope_node_id")
	private UUID scopeNodeId;

	@Column(name = "inherit_children", nullable = false)
	private boolean inheritChildren;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
