package io.github.edmaputra.iam.domain.security;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RedirectUriPolicy}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class RedirectUriPolicyTest {

	@Test
	@DisplayName("Should permit exact host matches case-insensitively")
	void shouldPermitExactHostMatches() {
		RedirectUriPolicy policy = RedirectUriPolicy.of("portal.clinic.org", "localhost");

		assertThat(policy.isAllowedHost("portal.clinic.org")).isTrue();
		assertThat(policy.isAllowedHost("PORTAL.CLINIC.ORG")).isTrue();
		assertThat(policy.isAllowedHost("localhost")).isTrue();
		assertThat(policy.isAllowedHost("evil.com")).isFalse();
	}

	@Test
	@DisplayName("Should extract host from full HTTP/HTTPS URIs")
	void shouldExtractHostFromFullUris() {
		RedirectUriPolicy policy = RedirectUriPolicy.of("https://app.example.com/oauth/callback", "http://dev.example.com:8080/cb");

		assertThat(policy.isAllowedHost("app.example.com")).isTrue();
		assertThat(policy.isAllowedHost("dev.example.com")).isTrue();
		assertThat(policy.isAllowedHost("other.example.com")).isFalse();
	}

	@Test
	@DisplayName("Should support wildcard domain patterns")
	void shouldSupportWildcardPatterns() {
		RedirectUriPolicy policy = RedirectUriPolicy.of("*.clinic.org");

		assertThat(policy.isAllowedHost("portal.clinic.org")).isTrue();
		assertThat(policy.isAllowedHost("admin.portal.clinic.org")).isTrue();
		assertThat(policy.isAllowedHost("clinic.org")).isTrue();
		assertThat(policy.isAllowedHost("otherclinic.org")).isFalse();
		assertThat(policy.isAllowedHost("evil-clinic.org")).isFalse();
	}

	@Test
	@DisplayName("Should safely handle empty, null, or blank inputs")
	void shouldHandleEmptyAndNullInputs() {
		RedirectUriPolicy empty = RedirectUriPolicy.empty();
		assertThat(empty.isAllowedHost("portal.clinic.org")).isFalse();
		assertThat(empty.isAllowedHost(null)).isFalse();
		assertThat(empty.isAllowedHost("   ")).isFalse();

		RedirectUriPolicy withNulls = new RedirectUriPolicy(null);
		assertThat(withNulls.allowedUris()).isEmpty();
	}
}
