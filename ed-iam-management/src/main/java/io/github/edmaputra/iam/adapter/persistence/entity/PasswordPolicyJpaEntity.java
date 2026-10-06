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
 * JPA entity representing a tenant password policy in the {@code iam_password_policy} table.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@Entity
@Table(name = "iam_password_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordPolicyJpaEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(name = "min_length", nullable = false)
	private int minLength;

	@Column(name = "max_length", nullable = false)
	private int maxLength;

	@Column(name = "min_uppercase", nullable = false)
	private int minUppercase;

	@Column(name = "min_lowercase", nullable = false)
	private int minLowercase;

	@Column(name = "min_numbers", nullable = false)
	private int minNumbers;

	@Column(name = "min_special_characters", nullable = false)
	private int minSpecialCharacters;

	@Column(name = "custom_regex", length = 500)
	private String customRegex;

	@Column(name = "regex_description", length = 500)
	private String regexDescription;

	@Column(name = "disallow_username", nullable = false)
	private boolean disallowUsername;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
