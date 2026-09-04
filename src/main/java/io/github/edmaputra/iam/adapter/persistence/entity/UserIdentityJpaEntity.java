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
 * JPA entity representing a federated identity link in the {@code iam_user_identity} table.
 *
 * @author edmaputra
 */
@Entity
@Table(name = "iam_user_identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentityJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "provider_type", nullable = false, length = 64)
	private String providerType;

	@Column(name = "external_subject_id", nullable = false, length = 255)
	private String externalSubjectId;

	@Column(name = "issuer_url", length = 512)
	private String issuerUrl;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
