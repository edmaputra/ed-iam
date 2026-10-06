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
 * JPA entity representing a permitted redirect URL or host in the {@code iam_allowed_redirect_uri} table.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@Entity
@Table(name = "iam_allowed_redirect_uri")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedirectUriJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(name = "uri", nullable = false, length = 500)
	private String uri;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
