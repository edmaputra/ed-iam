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
 * JPA entity representing a user's multi-factor authentication (MFA / TOTP) configuration
 * in the {@code iam_user_mfa} table.
 *
 * @author edmaputra
 * @since 0.5.0
 */
@Entity
@Table(name = "iam_user_mfa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserMfaJpaEntity {

	@Id
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "secret", nullable = false, length = 64)
	private String secret;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@Column(name = "backup_codes", length = 2048)
	private String backupCodes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
