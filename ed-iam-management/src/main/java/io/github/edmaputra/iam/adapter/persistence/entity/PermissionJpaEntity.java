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
 * JPA entity representing a permission in the {@code iam_permission} table.
 *
 * @author edmaputra
 * @since 0.10.0
 */
@Entity
@Table(name = "iam_permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionJpaEntity {

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

	@Column(name = "category", length = 64)
	private String category;

	@Column(name = "is_system_permission", nullable = false)
	private boolean isSystemPermission;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
