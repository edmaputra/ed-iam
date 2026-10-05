package io.github.edmaputra.iam.adapter.security.redirect;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.application.port.out.AllowedRedirectHostResolverPort;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Default properties-backed implementation of {@link AllowedRedirectHostResolverPort}.
 * <p>
 * Matches the requested host against the hostname of {@link MagicLinkProperties#baseUrl()}
 * as well as any explicitly configured {@link MagicLinkProperties#allowedRedirectHosts()}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class DefaultAllowedRedirectHostResolver implements AllowedRedirectHostResolverPort {

	private final MagicLinkProperties properties;

	public DefaultAllowedRedirectHostResolver(MagicLinkProperties properties) {
		this.properties = Objects.requireNonNull(properties, "MagicLinkProperties must not be null");
	}

	@Override
	public boolean isAllowedHost(String host, TenantId tenantId) {
		if (host == null || host.isBlank()) {
			return false;
		}

		String trimmedHost = host.trim();

		// Match base URL host
		try {
			URI baseUri = URI.create(properties.baseUrl());
			String baseHost = baseUri.getHost();
			if (baseHost != null && baseHost.equalsIgnoreCase(trimmedHost)) {
				return true;
			}
		}
		catch (Exception ignored) {
			// Ignore base URL parse issues
		}

		// Match configured allowed hosts
		List<String> allowedHosts = properties.allowedRedirectHosts();
		if (allowedHosts != null) {
			for (String allowed : allowedHosts) {
				if (trimmedHost.equalsIgnoreCase(allowed.trim())) {
					return true;
				}
			}
		}

		return false;
	}
}
