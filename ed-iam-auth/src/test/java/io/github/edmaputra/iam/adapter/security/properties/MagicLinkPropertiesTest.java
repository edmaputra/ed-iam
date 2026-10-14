package io.github.edmaputra.iam.adapter.security.properties;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MagicLinkProperties}.
 *
 * @author edmaputra
 * @since 0.11.0
 */
class MagicLinkPropertiesTest {

	@Test
	@DisplayName("Should apply defaults when null or invalid values are provided")
	void shouldApplyDefaults() {
		MagicLinkProperties properties = new MagicLinkProperties(null, 0L, null, null, null);

		assertThat(properties.enabled()).isTrue();
		assertThat(properties.expirationSeconds()).isEqualTo(900L);
		assertThat(properties.baseUrl()).isEqualTo("http://localhost:8080");
		assertThat(properties.allowedRedirectHosts()).isEmpty();
		assertThat(properties.endpoints()).isNotNull();
		assertThat(properties.endpoints().enabled()).isFalse();
	}

	@Test
	@DisplayName("Should retain custom properties and endpoint configuration")
	void shouldRetainCustomProperties() {
		MagicLinkProperties properties = new MagicLinkProperties(
				false,
				1200L,
				"https://clinic.org",
				List.of("clinic.org"),
				new MagicLinkProperties.Endpoints(true));

		assertThat(properties.enabled()).isFalse();
		assertThat(properties.expirationSeconds()).isEqualTo(1200L);
		assertThat(properties.baseUrl()).isEqualTo("https://clinic.org");
		assertThat(properties.allowedRedirectHosts()).containsExactly("clinic.org");
		assertThat(properties.endpoints().enabled()).isTrue();
	}

	@Test
	@DisplayName("Should support legacy 4-argument constructor with endpoints disabled")
	void shouldSupportLegacyConstructor() {
		MagicLinkProperties properties = new MagicLinkProperties(
				true,
				600L,
				"https://auth.clinic.org",
				List.of("app.clinic.org"));

		assertThat(properties.endpoints()).isNotNull();
		assertThat(properties.endpoints().enabled()).isFalse();
	}

	@Test
	@DisplayName("Default properties factory should produce default values with endpoints disabled")
	void shouldCreateDefaultProperties() {
		MagicLinkProperties defaults = MagicLinkProperties.defaultProperties();

		assertThat(defaults.enabled()).isTrue();
		assertThat(defaults.expirationSeconds()).isEqualTo(900L);
		assertThat(defaults.baseUrl()).isEqualTo("http://localhost:8080");
		assertThat(defaults.endpoints().enabled()).isFalse();
	}

	@Test
	@DisplayName("Endpoints record should default null enabled to false")
	void shouldDefaultNullEndpointsEnabledToFalse() {
		MagicLinkProperties.Endpoints endpoints = new MagicLinkProperties.Endpoints(null);

		assertThat(endpoints.enabled()).isFalse();
	}
}
