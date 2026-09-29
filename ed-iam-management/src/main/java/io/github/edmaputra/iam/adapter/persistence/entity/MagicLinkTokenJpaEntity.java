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
 * JPA entity representing a magic link token record in the {@code iam_magic_link_token} table.
 *
 * @author edmaputra
 * @since 0.5.0
 */
@Entity
@Table(name = "iam_magic_link_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MagicLinkTokenJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "token", nullable = false, unique = true, length = 128)
	private String token;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "tenant_id", length = 64)
	private String tenantId;

	@Column(name = "email", nullable = false, length = 255)
	private String email;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
