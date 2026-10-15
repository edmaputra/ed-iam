package io.github.edmaputra.iam.adapter.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter;
import io.github.edmaputra.iam.adapter.security.jwt.JwtProperties;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying the auto-configuration activation, deactivation,
 * and filter registration toggles in {@link IamResourceServerAutoConfiguration}.
 *
 * @author edmaputra
 * @since 0.12.0
 */
class IamResourceServerAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(IamResourceServerAutoConfiguration.class))
			.withPropertyValues("iam.jwt.secret=" + JwtProperties.TEST_SECRET);

	@Test
	@DisplayName("Should auto-configure Resource Server and Filter by default")
	void shouldAutoConfigureByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(JwtTokenProvider.class);
			assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
			assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
		});
	}

	@Test
	@DisplayName("Should not auto-configure Resource Server when ed-iam.resource-server.enabled is false")
	void shouldDisableResourceServerWhenConfigured() {
		contextRunner
				.withPropertyValues("ed-iam.resource-server.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean(JwtTokenProvider.class);
					assertThat(context).doesNotHaveBean(JwtAuthenticationFilter.class);
				});
	}

	@Test
	@DisplayName("Should omit JwtAuthenticationFilter bean when ed-iam.security.filter.enabled is false")
	void shouldOmitFilterWhenFilterEnabledIsFalse() {
		contextRunner
				.withPropertyValues("ed-iam.security.filter.enabled=false")
				.run(context -> {
					assertThat(context).hasSingleBean(JwtTokenProvider.class);
					assertThat(context).doesNotHaveBean(JwtAuthenticationFilter.class);
				});
	}

	@Test
	@DisplayName("Should disable servlet registration when ed-iam.security.filter.register-servlet is false")
	void shouldDisableServletRegistrationWhenRegisterServletIsFalse() {
		contextRunner
				.withPropertyValues("ed-iam.security.filter.register-servlet=false")
				.run(context -> {
					assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
					assertThat(context).hasSingleBean(FilterRegistrationBean.class);
					@SuppressWarnings("unchecked")
					FilterRegistrationBean<JwtAuthenticationFilter> registration =
							context.getBean(FilterRegistrationBean.class);
					assertThat(registration.isEnabled()).isFalse();
				});
	}
}
