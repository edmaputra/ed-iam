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
 * JPA entity representing an ABAC dynamic policy in the {@code iam_policy} table.
 *
 * @author edmaputra
 * @since 0.14.0
 */
@Entity
@Table(name = "iam_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PolicyJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	@Column(name = "description", length = 1024)
	private String description;

	@Column(name = "effect", nullable = false, length = 16)
	private String effect;

	@Column(name = "priority", nullable = false)
	private int priority;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@Column(name = "target_actions", length = 1024)
	private String targetActions;

	@Column(name = "target_resource_types", length = 1024)
	private String targetResourceTypes;

	@Column(name = "target_roles", length = 1024)
	private String targetRoles;

	@Column(name = "constraints_json")
	private String constraintsJson;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
