package io.github.edmaputra.iam.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpCidrMatcher Domain Unit Tests")
class IpCidrMatcherTest {

	@Test
	@DisplayName("Should correctly match IPv4 CIDRs")
	void shouldMatchIpv4Cidr() {
		assertThat(IpCidrMatcher.matches("192.168.1.50", "192.168.1.0/24")).isTrue();
		assertThat(IpCidrMatcher.matches("192.168.2.1", "192.168.1.0/24")).isFalse();
		assertThat(IpCidrMatcher.matches("10.50.12.3", "10.0.0.0/8")).isTrue();
		assertThat(IpCidrMatcher.matches("11.0.0.1", "10.0.0.0/8")).isFalse();
		assertThat(IpCidrMatcher.matches("172.16.5.10", "172.16.0.0/12")).isTrue();
		assertThat(IpCidrMatcher.matches("172.32.1.1", "172.16.0.0/12")).isFalse();
		assertThat(IpCidrMatcher.matches("127.0.0.1", "127.0.0.1")).isTrue();
		assertThat(IpCidrMatcher.matches("127.0.0.2", "127.0.0.1")).isFalse();
		assertThat(IpCidrMatcher.matches("127.0.0.1", "127.0.0.1/32")).isTrue();
		assertThat(IpCidrMatcher.matches("0.0.0.0", "0.0.0.0/0")).isTrue();
		assertThat(IpCidrMatcher.matches("255.255.255.255", "0.0.0.0/0")).isTrue();
	}

	@Test
	@DisplayName("Should correctly match IPv6 CIDRs")
	void shouldMatchIpv6Cidr() {
		assertThat(IpCidrMatcher.matches("::1", "::1/128")).isTrue();
		assertThat(IpCidrMatcher.matches("::1", "::1")).isTrue();
		assertThat(IpCidrMatcher.matches("::2", "::1/128")).isFalse();
		assertThat(IpCidrMatcher.matches("2001:db8:abcd:0012::1", "2001:db8::/32")).isTrue();
		assertThat(IpCidrMatcher.matches("2001:db9::1", "2001:db8::/32")).isFalse();
	}

	@Test
	@DisplayName("Should handle null, blank, and invalid IP inputs safely")
	void shouldHandleInvalidInputsSafely() {
		assertThat(IpCidrMatcher.matches(null, "192.168.1.0/24")).isFalse();
		assertThat(IpCidrMatcher.matches("", "192.168.1.0/24")).isFalse();
		assertThat(IpCidrMatcher.matches("192.168.1.1", null)).isFalse();
		assertThat(IpCidrMatcher.matches("192.168.1.1", "")).isFalse();
		assertThat(IpCidrMatcher.matches("not-an-ip", "192.168.1.0/24")).isFalse();
		assertThat(IpCidrMatcher.matches("192.168.1.1", "invalid-cidr")).isFalse();
		assertThat(IpCidrMatcher.matches("192.168.1.1", "192.168.1.0/999")).isFalse();
		// IPv4 vs IPv6 family mismatch
		assertThat(IpCidrMatcher.matches("192.168.1.1", "::1/128")).isFalse();
	}
}
