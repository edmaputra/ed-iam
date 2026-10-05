package io.github.edmaputra.iam.adapter.security.redirect;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAllowedRedirectHostResolverTest {

	@Test
	@DisplayName("Should allow host matching baseUrl")
	void shouldAllowHostMatchingBaseUrl() {
		MagicLinkProperties properties = new MagicLinkProperties(true, 900L, "https://auth.company.org/login", List.of());
		DefaultAllowedRedirectHostResolver resolver = new DefaultAllowedRedirectHostResolver(properties);

		assertThat(resolver.isAllowedHost("auth.company.org", null)).isTrue();
		assertThat(resolver.isAllowedHost("AUTH.COMPANY.ORG", TenantId.generate())).isTrue();
	}

	@Test
	@DisplayName("Should allow host in allowedRedirectHosts list")
	void shouldAllowHostInConfiguredList() {
		MagicLinkProperties properties = new MagicLinkProperties(
				true, 900L, "http://localhost:8080", List.of("portal.clinic.org", "app.partner.io"));
		DefaultAllowedRedirectHostResolver resolver = new DefaultAllowedRedirectHostResolver(properties);

		assertThat(resolver.isAllowedHost("portal.clinic.org", null)).isTrue();
		assertThat(resolver.isAllowedHost("app.partner.io", TenantId.generate())).isTrue();
	}

	@Test
	@DisplayName("Should reject unknown hosts")
	void shouldRejectUnknownHosts() {
		MagicLinkProperties properties = new MagicLinkProperties(
				true, 900L, "http://localhost:8080", List.of("portal.clinic.org"));
		DefaultAllowedRedirectHostResolver resolver = new DefaultAllowedRedirectHostResolver(properties);

		assertThat(resolver.isAllowedHost("evil.com", null)).isFalse();
		assertThat(resolver.isAllowedHost("attacker-portal.clinic.org", null)).isFalse();
		assertThat(resolver.isAllowedHost("", null)).isFalse();
		assertThat(resolver.isAllowedHost(null, null)).isFalse();
	}
}
