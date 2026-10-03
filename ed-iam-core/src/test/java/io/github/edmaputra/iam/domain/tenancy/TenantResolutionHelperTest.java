package io.github.edmaputra.iam.domain.tenancy;

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
	@DisplayName("resolveTenantId returns header UUID when present and valid")
	void resolveTenantId_withValidHeader_returnsHeaderUuid() {
		UUID expected = UUID.randomUUID();
		UUID fallback = UUID.randomUUID();

		UUID resolved = TenantResolutionHelper.resolveTenantId(expected.toString(), fallback);

		assertThat(resolved).isEqualTo(expected);
	}

	@Test
	@DisplayName("resolveTenantId returns fallback when header is null or blank")
	void resolveTenantId_withNullOrBlankHeader_returnsFallback() {
		UUID fallback = UUID.randomUUID();

		assertThat(TenantResolutionHelper.resolveTenantId(null, fallback)).isEqualTo(fallback);
		assertThat(TenantResolutionHelper.resolveTenantId("   ", fallback)).isEqualTo(fallback);
	}

	@Test
	@DisplayName("resolveTenantId throws IllegalArgumentException when neither header nor fallback is provided")
	void resolveTenantId_withNoHeaderAndNoFallback_throwsException() {
		assertThatThrownBy(() -> TenantResolutionHelper.resolveTenantId(null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Tenant ID must be specified");

		assertThatThrownBy(() -> TenantResolutionHelper.resolveTenantId("   ", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Tenant ID must be specified");
	}

	@Test
	@DisplayName("parseTenantHeader returns null when header is null or blank")
	void parseTenantHeader_withNullOrBlank_returnsNull() {
		assertThat(TenantResolutionHelper.parseTenantHeader(null)).isNull();
		assertThat(TenantResolutionHelper.parseTenantHeader("   ")).isNull();
	}

	@Test
	@DisplayName("parseTenantHeader parses valid UUID and trims whitespace")
	void parseTenantHeader_withValidUuid_returnsUuid() {
		UUID expected = UUID.randomUUID();

		assertThat(TenantResolutionHelper.parseTenantHeader(expected.toString())).isEqualTo(expected);
		assertThat(TenantResolutionHelper.parseTenantHeader("  " + expected + "  ")).isEqualTo(expected);
	}

	@Test
	@DisplayName("parseTenantHeader throws IllegalArgumentException when header contains malformed UUID")
	void parseTenantHeader_withMalformedUuid_throwsException() {
		assertThatThrownBy(() -> TenantResolutionHelper.parseTenantHeader("invalid-uuid"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid UUID string for X-Tenant-ID header");
	}

	@Test
	@DisplayName("resolveOptionalTenantId returns header UUID when valid")
	void resolveOptionalTenantId_withValidHeader_returnsHeaderUuid() {
		UUID expected = UUID.randomUUID();
		UUID fallback = UUID.randomUUID();

		assertThat(TenantResolutionHelper.resolveOptionalTenantId(expected.toString(), fallback)).isEqualTo(expected);
	}

	@Test
	@DisplayName("resolveOptionalTenantId returns fallback when header is absent")
	void resolveOptionalTenantId_withNullOrBlankHeader_returnsFallback() {
		UUID fallback = UUID.randomUUID();

		assertThat(TenantResolutionHelper.resolveOptionalTenantId(null, fallback)).isEqualTo(fallback);
		assertThat(TenantResolutionHelper.resolveOptionalTenantId("   ", fallback)).isEqualTo(fallback);
		assertThat(TenantResolutionHelper.resolveOptionalTenantId(null, null)).isNull();
		assertThat(TenantResolutionHelper.resolveOptionalTenantId("   ", null)).isNull();
	}

	@Test
	@DisplayName("Private constructor can be invoked via reflection for full coverage")
	void privateConstructor_coverage() throws Exception {
		Constructor<TenantResolutionHelper> constructor = TenantResolutionHelper.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		TenantResolutionHelper instance = constructor.newInstance();
		assertThat(instance).isNotNull();
	}
}
