package io.github.edmaputra.iam.application.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.adapter.security.redirect.DefaultAllowedRedirectHostResolver;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.application.port.out.AllowedRedirectHostResolverPort;
import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Focused application service responsible for issuing, storing, and dispatching magic link tokens.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class MagicLinkDispatchService {

	private static final String GENERIC_DISPATCH_MESSAGE =
			"If an account matching that email exists, a sign-in link has been sent to your inbox.";

	private final MagicLinkProperties magicLinkProperties;
	private final MagicLinkTokenStorePort magicLinkTokenStore;
	private final MagicLinkNotifierPort magicLinkNotifier;
	private final AllowedRedirectHostResolverPort allowedRedirectHostResolver;
	private final SecureRandom secureRandom = new SecureRandom();

	public MagicLinkDispatchService(
			MagicLinkProperties magicLinkProperties,
			MagicLinkTokenStorePort magicLinkTokenStore,
			MagicLinkNotifierPort magicLinkNotifier,
			AllowedRedirectHostResolverPort allowedRedirectHostResolver) {
		this.magicLinkProperties = magicLinkProperties != null ? magicLinkProperties : MagicLinkProperties.defaultProperties();
		this.magicLinkTokenStore = Objects.requireNonNull(magicLinkTokenStore, "MagicLinkTokenStorePort must not be null.");
		this.magicLinkNotifier = Objects.requireNonNull(magicLinkNotifier, "MagicLinkNotifierPort must not be null.");
		this.allowedRedirectHostResolver = allowedRedirectHostResolver != null
				? allowedRedirectHostResolver
				: new DefaultAllowedRedirectHostResolver(this.magicLinkProperties);
	}

	public MagicLinkDispatchService(
			MagicLinkProperties magicLinkProperties,
			MagicLinkTokenStorePort magicLinkTokenStore,
			MagicLinkNotifierPort magicLinkNotifier) {
		this(magicLinkProperties, magicLinkTokenStore, magicLinkNotifier, null);
	}

	public MagicLinkRequestResponse requestMagicLink(MagicLinkRequestCommand command, UserRepository userRepository) {
		Objects.requireNonNull(command, "MagicLinkRequestCommand must not be null.");

		if (!magicLinkProperties.enabled()) {
			throw new AuthenticationException("Magic link passwordless authentication is currently disabled.");
		}

		Optional<User> userOpt = userRepository.findByEmail(command.email().trim().toLowerCase());
		if (userOpt.isEmpty()) {
			// Anti-enumeration: return generic success without generating or dispatching a token
			return MagicLinkRequestResponse.of(GENERIC_DISPATCH_MESSAGE);
		}

		User user = userOpt.get();
		if (user.isSuspended() || user.isDeactivated()) {
			// Anti-enumeration: return generic success without dispatching
			return MagicLinkRequestResponse.of(GENERIC_DISPATCH_MESSAGE);
		}

		byte[] randomBytes = new byte[32];
		secureRandom.nextBytes(randomBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(magicLinkProperties.expirationSeconds());

		MagicLinkToken magicLinkToken = MagicLinkToken.issue(
				token,
				user.getId(),
				command.tenantId(),
				user.getEmail(),
				expiresAt,
				now);

		magicLinkTokenStore.save(magicLinkToken);

		String base = magicLinkProperties.baseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String verificationUrl = base + "/api/v1/auth/magic-link/verify?token=" + token;
		if (command.redirectUrl() != null && !command.redirectUrl().isBlank()) {
			validateRedirectUrl(command.redirectUrl(), command.tenantId());
			verificationUrl += "&redirect=" + URLEncoder.encode(command.redirectUrl().trim(), StandardCharsets.UTF_8);
		}

		magicLinkNotifier.sendMagicLink(magicLinkToken, verificationUrl);

		return MagicLinkRequestResponse.of(GENERIC_DISPATCH_MESSAGE);
	}

	/**
	 * Validates the requested redirect URL against dangerous schemes, protocol-relative bypasses,
	 * and unlisted external target hosts (OWASP A10 / CWE-601 Open Redirect prevention).
	 *
	 * @param redirectUrl candidate redirect URL
	 * @param tenantId    tenant context, or {@code null} if un-scoped
	 * @throws IllegalArgumentException if the redirect URL is untrusted, malformed, or targets an external host
	 */
	void validateRedirectUrl(String redirectUrl, TenantId tenantId) {
		if (redirectUrl == null || redirectUrl.isBlank()) {
			return;
		}

		String trimmed = redirectUrl.trim();
		String lower = trimmed.toLowerCase();

		if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
			throw new IllegalArgumentException("Dangerous or unsupported redirect URL scheme: " + redirectUrl);
		}

		// Relative path: Must start with single '/', NOT '//' or '/\' (protocol-relative / backslash tricks)
		if (trimmed.startsWith("/")) {
			if (trimmed.startsWith("//") || trimmed.startsWith("/\\") || trimmed.contains("\\")) {
				throw new IllegalArgumentException("Malformed relative redirect URL: " + redirectUrl);
			}
			return;
		}

		try {
			URI uri = URI.create(trimmed);
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
				throw new IllegalArgumentException("Redirect URL must use HTTP or HTTPS: " + redirectUrl);
			}

			String targetHost = uri.getHost();
			if (targetHost == null || targetHost.isBlank()) {
				throw new IllegalArgumentException("Redirect URL must specify a valid host: " + redirectUrl);
			}

			if (allowedRedirectHostResolver.isAllowedHost(targetHost, tenantId)) {
				return;
			}

			throw new IllegalArgumentException("Redirect URL host '" + targetHost + "' is not in the allowed redirect hosts list.");
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid redirect URL: " + redirectUrl, ex);
		}
	}

	void validateRedirectUrl(String redirectUrl) {
		validateRedirectUrl(redirectUrl, null);
	}
}
