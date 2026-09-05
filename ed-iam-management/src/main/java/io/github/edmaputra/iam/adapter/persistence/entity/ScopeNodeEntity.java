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
 * JPA entity representing a scope hierarchy node in the {@code iam_scope_node} table.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@Entity
@Table(name = "iam_scope_node")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScopeNodeEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "parent_id")
	private UUID parentId;

	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	@Column(name = "path", nullable = false, length = 512)
	private String path;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
