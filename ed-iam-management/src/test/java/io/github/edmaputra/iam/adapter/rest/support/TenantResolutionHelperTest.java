package io.github.edmaputra.iam.adapter.rest.support;

import java.lang.reflect.Constructor;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link TenantResolutionHelper}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class TenantResolutionHelperTest {

	@Test
	@DisplayName("Should resolve tenant ID from header when present")
	void shouldResolveFromHeader() {
		UUID expected = UUID.randomUUID();
		UUID fallback = UUID.randomUUID();

		UUID resolved = TenantResolutionHelper.resolveTenantId(expected.toString(), fallback);
		assertThat(resolved).isEqualTo(expected);
	}

	@Test
	@DisplayName("Should resolve tenant ID from fallback when header is null or blank")
	void shouldResolveFromFallback() {
		UUID fallback = UUID.randomUUID();

		assertThat(TenantResolutionHelper.resolveTenantId(null, fallback)).isEqualTo(fallback);
		assertThat(TenantResolutionHelper.resolveTenantId("   ", fallback)).isEqualTo(fallback);
	}

	@Test
	@DisplayName("Should throw IllegalArgumentException when both header and fallback are missing")
	void shouldThrowWhenBothMissing() {
		assertThatThrownBy(() -> TenantResolutionHelper.resolveTenantId(null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TenantResolutionHelper.resolveTenantId("   ", null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should cover private constructor")
	void shouldCoverPrivateConstructor() throws Exception {
		Constructor<TenantResolutionHelper> constructor = TenantResolutionHelper.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		constructor.newInstance();
	}
}
