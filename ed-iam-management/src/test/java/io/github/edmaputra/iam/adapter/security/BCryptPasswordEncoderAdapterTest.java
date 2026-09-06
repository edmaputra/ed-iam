package io.github.edmaputra.iam.adapter.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test verifying BCryptPasswordEncoderAdapter password hashing and matching.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class BCryptPasswordEncoderAdapterTest {

	@Test
	@DisplayName("Should encode and match passwords using default and custom strength")
	void shouldEncodeAndMatchPasswords() {
		BCryptPasswordEncoderAdapter defaultAdapter = new BCryptPasswordEncoderAdapter();
		String raw = "P@ssword123!";
		String hash1 = defaultAdapter.encode(raw);

		assertThat(hash1).isNotBlank().startsWith("$2a$");
		assertThat(defaultAdapter.matches(raw, hash1)).isTrue();
		assertThat(defaultAdapter.matches("wrong-password", hash1)).isFalse();

		BCryptPasswordEncoderAdapter customAdapter = new BCryptPasswordEncoderAdapter(6);
		String hash2 = customAdapter.encode(raw);
		assertThat(customAdapter.matches(raw, hash2)).isTrue();
	}
}
