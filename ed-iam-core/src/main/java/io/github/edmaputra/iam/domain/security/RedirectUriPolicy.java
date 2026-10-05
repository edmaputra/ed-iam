package io.github.edmaputra.iam.domain.security;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain record encapsulating permitted redirect URLs or host patterns for post-authentication redirects.
 * Enforces OWASP A10 / CWE-601 Open Redirect protections.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public record RedirectUriPolicy(Set<String> allowedUris) {

	public RedirectUriPolicy {
		if (allowedUris == null) {
			allowedUris = Set.of();
		}
		else {
			Set<String> cleaned = new LinkedHashSet<>();
			for (String uri : allowedUris) {
				if (uri != null && !uri.isBlank()) {
					cleaned.add(uri.trim());
				}
			}
			allowedUris = Collections.unmodifiableSet(cleaned);
		}
	}

	public static RedirectUriPolicy of(String... uris) {
		return new RedirectUriPolicy(Set.of(uris));
	}

	public static RedirectUriPolicy of(Iterable<String> uris) {
		if (uris == null) {
			return new RedirectUriPolicy(Set.of());
		}
		Set<String> set = new LinkedHashSet<>();
		uris.forEach(set::add);
		return new RedirectUriPolicy(set);
	}

	public static RedirectUriPolicy empty() {
		return new RedirectUriPolicy(Set.of());
	}

	/**
	 * Determines whether a candidate target host is permitted by this policy.
	 * Matches against:
	 * 1. Exact host equality (case-insensitive, e.g. "portal.clinic.org")
	 * 2. Host extracted from a full URI (e.g. "https://portal.clinic.org/callback")
	 * 3. Wildcard domain patterns (e.g. "*.clinic.org" matching "portal.clinic.org" or "clinic.org")
	 *
	 * @param candidateHost candidate target host
	 * @return {@code true} if allowed; {@code false} otherwise
	 */
	public boolean isAllowedHost(String candidateHost) {
		if (candidateHost == null || candidateHost.isBlank()) {
			return false;
		}
		String target = candidateHost.trim().toLowerCase();

		for (String item : allowedUris) {
			if (item == null || item.isBlank()) {
				continue;
			}
			String allowed = item.trim().toLowerCase();

			// 1. Direct match (e.g. "portal.clinic.org" == "portal.clinic.org")
			if (target.equals(allowed)) {
				return true;
			}

			// 2. Full URL host match (e.g. "https://portal.clinic.org/callback")
			String hostFromUri = extractHost(allowed);
			if (hostFromUri != null && target.equals(hostFromUri.toLowerCase())) {
				return true;
			}

			// 3. Wildcard pattern match (e.g. "*.clinic.org")
			if (matchWildcard(target, allowed) || (hostFromUri != null && matchWildcard(target, hostFromUri))) {
				return true;
			}
		}
		return false;
	}

	private static String extractHost(String value) {
		if (value.startsWith("http://") || value.startsWith("https://")) {
			try {
				return URI.create(value).getHost();
			}
			catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}

	private static boolean matchWildcard(String target, String pattern) {
		if (pattern.startsWith("*.")) {
			String domain = pattern.substring(2);
			return target.endsWith("." + domain) || target.equals(domain);
		}
		return false;
	}
}
