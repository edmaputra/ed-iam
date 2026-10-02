package io.github.edmaputra.iam.it;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.adapter.persistence.entity.MagicLinkTokenJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.MagicLinkTokenJpaRepository;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying passwordless Magic Link authentication against real PostgreSQL:
 * <ul>
 *   <li>Magic link request via REST and database token persistence in PostgreSQL ({@code iam_magic_link_token})</li>
 *   <li>POST and GET token verification and JWT session issuance</li>
 *   <li>Atomic token consumption and replay attack rejection (HTTP 401)</li>
 *   <li>Rejection of expired magic links (HTTP 401)</li>
 *   <li>Database cleanup of expired and consumed tokens via {@link MagicLinkTokenStorePort}</li>
 *   <li>404 handling when requesting magic links for unknown users</li>
 * </ul>
 *
 * @author edmaputra
 * @since 0.6.0
 */
class MagicLinkAuthenticationIT extends AbstractIntegrationTest {

	@Autowired
	private MagicLinkTokenJpaRepository magicLinkTokenJpaRepository;

	@Autowired
	private MagicLinkTokenStorePort magicLinkTokenStorePort;

	@Test
	@DisplayName("Should successfully request, persist in PostgreSQL, verify via POST, and access protected endpoints")
	void shouldSuccessfullyRequestAndVerifyMagicLinkViaPost() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		String email = "magic-user-" + UUID.randomUUID() + "@hospital.org";

		// 1. Seed user, role, and role assignment
		User user = User.create(email, passwordEncoder.encode("ignoredPassword"), "Dr. Magic Clinician", false);
		userRepository.save(user);

		Role role = Role.createCustom(tenantId, "PHYSICIAN", "Physician", "Attending physician", Set.of("PATIENT_READ", "PATIENT_WRITE"));
		roleRepository.save(role);

		UserRoleAssignment assignment = UserRoleAssignment.createTenantWide(user.getId(), role.getId(), tenantId);
		userRoleAssignmentRepository.save(assignment);

		// 2. Request magic link via POST /api/v1/auth/magic-link/request
		String requestBody = """
				{
				    "email": "%s"
				}
				""".formatted(email);

		byte[] requestBytes = webTestClient.post()
				.uri("/api/v1/auth/magic-link/request")
				.header("X-Tenant-ID", tenantUuid.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(requestBody)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.message").isNotEmpty()
				.jsonPath("$.token").isNotEmpty()
				.jsonPath("$.expiresAt").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(requestBytes).isNotNull();
		String token = JsonPath.read(new String(requestBytes, StandardCharsets.UTF_8), "$.token");
		assertThat(token).isNotBlank();

		// 3. Inspect PostgreSQL database row in iam_magic_link_token table directly
		Optional<MagicLinkTokenJpaEntity> tokenEntityOpt = magicLinkTokenJpaRepository.findByToken(token);
		assertThat(tokenEntityOpt).isPresent();
		MagicLinkTokenJpaEntity tokenEntity = tokenEntityOpt.get();
		assertThat(tokenEntity.getUserId()).isEqualTo(user.getId().value());
		assertThat(tokenEntity.getTenantId()).isEqualTo(tenantUuid.toString());
		assertThat(tokenEntity.getEmail()).isEqualTo(email);
		assertThat(tokenEntity.getConsumedAt()).isNull();
		assertThat(tokenEntity.getExpiresAt()).isAfter(Instant.now());

		// 4. Verify magic link via POST /api/v1/auth/magic-link/verify
		String verifyBody = """
				{
				    "token": "%s"
				}
				""".formatted(token);

		String expectedVerifyJson = """
				{
				    "tokenType": "Bearer",
				    "user": {
				        "email": "%s",
				        "fullName": "Dr. Magic Clinician",
				        "tenantId": "%s"
				    }
				}
				""".formatted(email, tenantUuid);

		byte[] verifyBytes = webTestClient.post()
				.uri("/api/v1/auth/magic-link/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(verifyBody)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedVerifyJson, JsonCompareMode.LENIENT)
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.refreshToken").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(verifyBytes).isNotNull();
		String accessToken = JsonPath.read(new String(verifyBytes, StandardCharsets.UTF_8), "$.accessToken");
		assertThat(accessToken).isNotBlank();

		// 5. Verify PostgreSQL database row has been marked as consumed
		Optional<MagicLinkTokenJpaEntity> consumedEntityOpt = magicLinkTokenJpaRepository.findByToken(token);
		assertThat(consumedEntityOpt).isPresent();
		assertThat(consumedEntityOpt.get().getConsumedAt()).isNotNull();

		// 6. Access protected endpoint /api/v1/auth/me with the issued Bearer access token
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.header("Authorization", "Bearer " + accessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo(email)
				.jsonPath("$.fullName").isEqualTo("Dr. Magic Clinician")
				.jsonPath("$.tenantId").isEqualTo(tenantUuid.toString())
				.jsonPath("$.permissions").isArray();

		// 7. Replay attack prevention: reusing the consumed token MUST fail with 401 Unauthorized
		webTestClient.post()
				.uri("/api/v1/auth/magic-link/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(verifyBody)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should successfully verify magic link via GET endpoint (simulating email direct link click)")
	void shouldSuccessfullyVerifyMagicLinkViaGet() {
		String email = "direct-link-" + UUID.randomUUID() + "@hospital.org";
		User user = User.create(email, passwordEncoder.encode("ignoredPassword"), "Dr. Direct Click", false);
		userRepository.save(user);

		// Request magic link
		byte[] requestBytes = webTestClient.post()
				.uri("/api/v1/auth/magic-link/request")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s"
						}
						""".formatted(email))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult()
				.getResponseBody();

		String token = JsonPath.read(new String(requestBytes, StandardCharsets.UTF_8), "$.token");

		// Verify via GET /api/v1/auth/magic-link/verify?token=...
		webTestClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/api/v1/auth/magic-link/verify")
						.queryParam("token", token)
						.build())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.user.email").isEqualTo(email);

		// Assert token consumed in PostgreSQL
		Optional<MagicLinkTokenJpaEntity> tokenEntity = magicLinkTokenJpaRepository.findByToken(token);
		assertThat(tokenEntity).isPresent();
		assertThat(tokenEntity.get().getConsumedAt()).isNotNull();
	}

	@Test
	@DisplayName("Should reject expired magic link tokens with 401 Unauthorized")
	void shouldRejectExpiredMagicLink() {
		String email = "expired-user-" + UUID.randomUUID() + "@hospital.org";
		User user = User.create(email, passwordEncoder.encode("ignoredPassword"), "Expired User", false);
		userRepository.save(user);

		String expiredTokenValue = "expired-" + UUID.randomUUID();
		Instant now = Instant.now();
		MagicLinkToken expiredToken = MagicLinkToken.issue(
				expiredTokenValue,
				user.getId(),
				null,
				email,
				now.minus(10, ChronoUnit.MINUTES),
				now.minus(25, ChronoUnit.MINUTES));

		// Persist directly into PostgreSQL via MagicLinkTokenStorePort
		magicLinkTokenStorePort.save(expiredToken);

		// Attempting verification must fail with 401 Unauthorized
		webTestClient.post()
				.uri("/api/v1/auth/magic-link/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "token": "%s"
						}
						""".formatted(expiredTokenValue))
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should purge expired and consumed tokens from PostgreSQL database")
	void shouldPurgeExpiredAndConsumedTokensFromDatabase() {
		String email = "cleanup-user-" + UUID.randomUUID() + "@hospital.org";
		User user = User.create(email, passwordEncoder.encode("ignoredPassword"), "Cleanup User", false);
		userRepository.save(user);

		Instant now = Instant.now();

		// 1. Save an expired token
		String expiredTokenStr = "clean-expired-" + UUID.randomUUID();
		MagicLinkToken expiredToken = MagicLinkToken.issue(
				expiredTokenStr, user.getId(), null, email, now.minus(5, ChronoUnit.MINUTES), now.minus(20, ChronoUnit.MINUTES));
		magicLinkTokenStorePort.save(expiredToken);

		// 2. Save a consumed token
		String consumedTokenStr = "clean-consumed-" + UUID.randomUUID();
		MagicLinkToken consumedToken = MagicLinkToken.issue(
				consumedTokenStr, user.getId(), null, email, now.plus(15, ChronoUnit.MINUTES), now);
		magicLinkTokenStorePort.save(consumedToken);
		magicLinkTokenStorePort.consume(consumedTokenStr, now);

		// 3. Save an active unconsumed token
		String activeTokenStr = "clean-active-" + UUID.randomUUID();
		MagicLinkToken activeToken = MagicLinkToken.issue(
				activeTokenStr, user.getId(), null, email, now.plus(15, ChronoUnit.MINUTES), now);
		magicLinkTokenStorePort.save(activeToken);

		// 4. Perform purge
		int deletedCount = magicLinkTokenStorePort.deleteExpired(now);
		assertThat(deletedCount).isGreaterThanOrEqualTo(2);

		// 5. Assert database state
		assertThat(magicLinkTokenJpaRepository.findByToken(expiredTokenStr)).isEmpty();
		assertThat(magicLinkTokenJpaRepository.findByToken(consumedTokenStr)).isEmpty();
		assertThat(magicLinkTokenJpaRepository.findByToken(activeTokenStr)).isPresent();
	}

	@Test
	@DisplayName("Should return 404 Not Found when requesting magic link for non-existent email")
	void shouldReturn404WhenRequestingMagicLinkForNonExistentUser() {
		webTestClient.post()
				.uri("/api/v1/auth/magic-link/request")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "unknown-nonexistent@hospital.org"
						}
						""")
				.exchange()
				.expectStatus().isNotFound();
	}
}
