package io.github.edmaputra.iam.adapter.security.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MfaProperties}.
 *
 * @author edmaputra
 * @since 0.11.0
 */
class MfaPropertiesTest {

	@Test
	@DisplayName("Should apply defaults when null or blank values are provided")
	void shouldApplyDefaults() {
		MfaProperties properties = new MfaProperties(null, null);

		assertThat(properties.issuer()).isEqualTo("ed-iam");
		assertThat(properties.endpoints()).isNotNull();
		assertThat(properties.endpoints().enabled()).isFalse();
	}

	@Test
	@DisplayName("Should retain custom issuer and endpoints configuration")
	void shouldRetainCustomConfiguration() {
		MfaProperties properties = new MfaProperties("CustomClinic", new MfaProperties.Endpoints(true));

		assertThat(properties.issuer()).isEqualTo("CustomClinic");
		assertThat(properties.endpoints().enabled()).isTrue();
	}

	@Test
	@DisplayName("Should create defaultProperties with endpoints disabled")
	void shouldCreateDefaultProperties() {
		MfaProperties defaults = MfaProperties.defaultProperties();

		assertThat(defaults.issuer()).isEqualTo("ed-iam");
		assertThat(defaults.endpoints().enabled()).isFalse();
	}

	@Test
	@DisplayName("Endpoints record should default null enabled to false")
	void shouldDefaultNullEndpointsEnabledToFalse() {
		MfaProperties.Endpoints endpoints = new MfaProperties.Endpoints(null);

		assertThat(endpoints.enabled()).isFalse();
	}
}
