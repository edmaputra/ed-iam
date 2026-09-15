package io.github.edmaputra.iam.adapter.persistence.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a role catalog record in the {@code iam_role} table.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Entity
@Table(name = "iam_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	@Column(name = "description", length = 512)
	private String description;

	@Column(name = "is_system_role", nullable = false)
	private boolean isSystemRole;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "iam_role_permission", joinColumns = @JoinColumn(name = "role_id"))
	@Column(name = "permission", nullable = false, length = 128)
	private Set<String> permissions = new HashSet<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
