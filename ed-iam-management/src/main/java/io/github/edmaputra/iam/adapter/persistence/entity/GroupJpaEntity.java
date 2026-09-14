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
 * JPA entity representing a user group in the {@code iam_group} table.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Entity
@Table(name = "iam_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	@Column(name = "description", length = 512)
	private String description;

	@Column(name = "external_idp_group_name", length = 255)
	private String externalIdpGroupName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
