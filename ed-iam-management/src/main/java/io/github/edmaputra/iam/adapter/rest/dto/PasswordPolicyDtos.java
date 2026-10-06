package io.github.edmaputra.iam.adapter.rest.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Request and response DTOs for password policy administrative endpoints.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public final class PasswordPolicyDtos {

	private PasswordPolicyDtos() {}

	/**
	 * DTO representing password policy response.
	 */
	public record PasswordPolicyResponse(
			UUID tenantId,
			int minLength,
			int maxLength,
			int minUppercase,
			int minLowercase,
			int minNumbers,
			int minSpecialCharacters,
			String customRegex,
			String regexDescription,
			boolean disallowUsername) {

		public static PasswordPolicyResponse fromDomain(TenantId tenantId, PasswordPolicy policy) {
			return new PasswordPolicyResponse(
					tenantId != null ? tenantId.value() : null,
					policy.minLength(),
					policy.maxLength(),
					policy.minUppercase(),
					policy.minLowercase(),
					policy.minNumbers(),
					policy.minSpecialCharacters(),
					policy.customRegex(),
					policy.regexDescription(),
					policy.disallowUsername()
			);
		}
	}

	/**
	 * DTO representing request to update runtime password policy.
	 */
	public record UpdatePasswordPolicyRequest(
			UUID tenantId,

			@Min(value = 1, message = "minLength must be at least 1.")
			@Max(value = 256, message = "minLength cannot exceed 256.")
			Integer minLength,

			@Min(value = 1, message = "maxLength must be at least 1.")
			@Max(value = 256, message = "maxLength cannot exceed 256.")
			Integer maxLength,

			@Min(value = 0, message = "minUppercase cannot be negative.")
			Integer minUppercase,

			@Min(value = 0, message = "minLowercase cannot be negative.")
			Integer minLowercase,

			@Min(value = 0, message = "minNumbers cannot be negative.")
			Integer minNumbers,

			@Min(value = 0, message = "minSpecialCharacters cannot be negative.")
			Integer minSpecialCharacters,

			@Size(max = 500, message = "customRegex must not exceed 500 characters.")
			String customRegex,

			@Size(max = 500, message = "regexDescription must not exceed 500 characters.")
			String regexDescription,

			Boolean disallowUsername) {

		public PasswordPolicy toDomain(PasswordPolicy currentPolicy) {
			PasswordPolicy base = currentPolicy != null ? currentPolicy : PasswordPolicy.defaultPolicy();
			return new PasswordPolicy(
					minLength != null ? minLength : base.minLength(),
					maxLength != null ? maxLength : base.maxLength(),
					minUppercase != null ? minUppercase : base.minUppercase(),
					minLowercase != null ? minLowercase : base.minLowercase(),
					minNumbers != null ? minNumbers : base.minNumbers(),
					minSpecialCharacters != null ? minSpecialCharacters : base.minSpecialCharacters(),
					customRegex != null ? (customRegex.isBlank() ? null : customRegex.trim()) : base.customRegex(),
					regexDescription != null ? (regexDescription.isBlank() ? null : regexDescription.trim()) : base.regexDescription(),
					disallowUsername != null ? disallowUsername : base.disallowUsername()
			);
		}
	}
}
