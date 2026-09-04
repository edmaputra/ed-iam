package io.github.edmaputra.iam.adapter.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Provider responsible for issuing and verifying HMAC-SHA256 signed JSON Web Tokens (JWT).
 * Embeds tenant context, effective roles, permissions, and hierarchical scopes into access token claims.
 *
 * @author edmaputra
 */
public class JwtTokenProvider {

	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_TENANT_ID = "tenantId";
	private static final String CLAIM_SUPER_ADMIN = "isSuperAdmin";
	private static final String CLAIM_TENANT_WIDE = "isTenantWide";
	private static final String CLAIM_GROUPS = "groups";
	private static final String CLAIM_ROLES = "roles";
	private static final String CLAIM_PERMISSIONS = "permissions";
	private static final String CLAIM_SCOPE_NODE_IDS = "scopeNodeIds";
	private static final String CLAIM_SCOPE_PATHS = "scopePaths";
	private static final String CLAIM_TOKEN_TYPE = "tokenType";

	private static final String TYPE_ACCESS = "ACCESS";
	private static final String TYPE_REFRESH = "REFRESH";

	private final SecretKey secretKey;
	private final long accessTokenExpirationSeconds;
	private final long refreshTokenExpirationSeconds;

	/**
	 * Constructs the provider using the specified JWT properties.
	 *
	 * @param properties configuration properties containing secret key and TTL settings
	 */
	public JwtTokenProvider(JwtProperties properties) {
		Objects.requireNonNull(properties, "JwtProperties must not be null.");
		this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpirationSeconds = properties.accessTokenExpirationSeconds();
		this.refreshTokenExpirationSeconds = properties.refreshTokenExpirationSeconds();
	}

	/**
	 * Issues a signed access token carrying the user's effective permissions and scope boundaries.
	 *
	 * @param access the resolved effective access model
	 * @return compact signed JWT string
	 */
	public String createAccessToken(EffectiveAccess access) {
		Objects.requireNonNull(access, "EffectiveAccess must not be null.");
		Instant now = Instant.now();
		Instant expiry = now.plusSeconds(accessTokenExpirationSeconds);

		List<String> scopeNodeIdStrings = access.accessibleScopeNodeIds().stream()
				.map(UUID::toString)
				.toList();

		return Jwts.builder()
				.subject(access.userId().value().toString())
				.claim(CLAIM_EMAIL, access.email())
				.claim(CLAIM_TENANT_ID, access.tenantId() == null ? null : access.tenantId().value().toString())
				.claim(CLAIM_SUPER_ADMIN, access.platformSuperAdmin())
				.claim(CLAIM_TENANT_WIDE, access.tenantWide())
				.claim(CLAIM_GROUPS, List.copyOf(access.groups()))
				.claim(CLAIM_ROLES, List.copyOf(access.roles()))
				.claim(CLAIM_PERMISSIONS, List.copyOf(access.permissions()))
				.claim(CLAIM_SCOPE_NODE_IDS, scopeNodeIdStrings)
				.claim(CLAIM_SCOPE_PATHS, List.copyOf(access.accessibleScopePaths()))
				.claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiry))
				.signWith(secretKey)
				.compact();
	}

	/**
	 * Issues a signed refresh token.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID context
	 * @return compact signed JWT refresh token string
	 */
	public String createRefreshToken(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Instant now = Instant.now();
		Instant expiry = now.plusSeconds(refreshTokenExpirationSeconds);

		return Jwts.builder()
				.subject(userId.value().toString())
				.claim(CLAIM_TENANT_ID, tenantId == null ? null : tenantId.value().toString())
				.claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiry))
				.signWith(secretKey)
				.compact();
	}

	/**
	 * Verifies the signature and expiration of a JWT token string.
	 *
	 * @param token the compact JWT token string
	 * @return true if valid and not expired
	 */
	public boolean validateToken(String token) {
		if (token == null || token.isBlank()) {
			return false;
		}
		try {
			Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token.trim());
			return true;
		}
		catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	/**
	 * Parses an access token and constructs the corresponding {@link CurrentActor}.
	 *
	 * @param token the compact access token string
	 * @return the verified {@link CurrentActor}
	 * @throws AuthenticationException if the token is invalid, expired, or not of type ACCESS
	 */
	public CurrentActor parseAccessToken(String token) {
		Claims claims = parseClaims(token);

		String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
		if (!TYPE_ACCESS.equalsIgnoreCase(tokenType)) {
			throw new AuthenticationException("Provided token is not an ACCESS token.");
		}

		UUID userId = UUID.fromString(claims.getSubject());
		String email = claims.get(CLAIM_EMAIL, String.class);
		String tenantIdStr = claims.get(CLAIM_TENANT_ID, String.class);
		UUID tenantId = (tenantIdStr == null || tenantIdStr.isBlank()) ? null : UUID.fromString(tenantIdStr);

		Boolean isSuperAdmin = claims.get(CLAIM_SUPER_ADMIN, Boolean.class);
		Boolean isTenantWide = claims.get(CLAIM_TENANT_WIDE, Boolean.class);

		Set<String> groups = extractStringSet(claims, CLAIM_GROUPS);
		Set<String> roles = extractStringSet(claims, CLAIM_ROLES);
		Set<String> permissions = extractStringSet(claims, CLAIM_PERMISSIONS);
		Set<String> scopePaths = extractStringSet(claims, CLAIM_SCOPE_PATHS);

		Set<UUID> scopeNodeIds = extractUuidSet(claims, CLAIM_SCOPE_NODE_IDS);

		return new SecurityContextCurrentActor(
				userId,
				email,
				tenantId,
				Boolean.TRUE.equals(isSuperAdmin),
				Boolean.TRUE.equals(isTenantWide),
				groups,
				roles,
				permissions,
				scopeNodeIds,
				scopePaths);
	}

	/**
	 * Parses a refresh token and extracts its payload claims.
	 *
	 * @param token the compact refresh token string
	 * @return the parsed {@link RefreshTokenClaims}
	 * @throws AuthenticationException if the token is invalid, expired, or not of type REFRESH
	 */
	public RefreshTokenClaims parseRefreshToken(String token) {
		Claims claims = parseClaims(token);

		String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
		if (!TYPE_REFRESH.equalsIgnoreCase(tokenType)) {
			throw new AuthenticationException("Provided token is not a REFRESH token.");
		}

		UUID userId = UUID.fromString(claims.getSubject());
		String tenantIdStr = claims.get(CLAIM_TENANT_ID, String.class);
		TenantId tenantId = (tenantIdStr == null || tenantIdStr.isBlank()) ? null : new TenantId(UUID.fromString(tenantIdStr));

		Instant issuedAt = claims.getIssuedAt() == null ? Instant.now() : claims.getIssuedAt().toInstant();
		Instant expiresAt = claims.getExpiration() == null ? Instant.now() : claims.getExpiration().toInstant();

		return new RefreshTokenClaims(new UserId(userId), tenantId, issuedAt, expiresAt);
	}

	/**
	 * Returns configured access token validity duration in seconds.
	 *
	 * @return access token expiration in seconds
	 */
	public long getAccessTokenExpirationSeconds() {
		return accessTokenExpirationSeconds;
	}

	private Claims parseClaims(String token) {
		try {
			return Jwts.parser()
					.verifyWith(secretKey)
					.build()
					.parseSignedClaims(token.trim())
					.getPayload();
		}
		catch (JwtException | IllegalArgumentException ex) {
			throw new AuthenticationException("Invalid or expired JWT token: " + ex.getMessage(), ex);
		}
	}

	private static Set<String> extractStringSet(Claims claims, String claimKey) {
		List<?> rawList = claims.get(claimKey, List.class);
		if (rawList == null) {
			return Set.of();
		}
		return rawList.stream()
				.map(Object::toString)
				.collect(Collectors.toUnmodifiableSet());
	}

	private static Set<UUID> extractUuidSet(Claims claims, String claimKey) {
		List<?> rawList = claims.get(claimKey, List.class);
		if (rawList == null) {
			return Set.of();
		}
		Set<UUID> uuids = new HashSet<>();
		for (Object item : rawList) {
			try {
				uuids.add(UUID.fromString(item.toString()));
			}
			catch (IllegalArgumentException ignored) {
			}
		}
		return Set.copyOf(uuids);
	}
}
