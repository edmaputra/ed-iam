package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.MfaChallengeClaims;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.port.in.MfaLoginVerifyCommand;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.auth.mfa.TotpGenerator;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.InvalidTotpException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MfaService}.
 *
 * @author edmaputra
 * @since 0.6.0
 */
@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

	@Mock
	private UserMfaRepository userMfaRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoderPort passwordEncoder;

	@Mock
	private TokenProviderPort tokenProvider;

	@Mock
	private EffectiveAccessResolver effectiveAccessResolver;

	private MfaService mfaService;

	private UserId testUserId;
	private User testUser;

	@BeforeEach
	void setUp() {
		mfaService = new MfaService(
				userMfaRepository,
				userRepository,
				passwordEncoder,
				tokenProvider,
				effectiveAccessResolver);

		testUserId = UserId.generate();
		testUser = new User(
				testUserId,
				"clinician@hospital.org",
				"$2a$10$hashedpassword",
				"Dr. Clinician",
				UserStatus.ACTIVE,
				false,
				Instant.now(),
				Instant.now());
	}

	@Test
	@DisplayName("Should successfully initiate MFA setup and generate secret, QR URI, and backup codes")
	void shouldInitiateSetup() {
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.empty());

		MfaSetupResponse response = mfaService.initiateSetup(testUserId, "Hospital-IAM");

		assertThat(response.secret()).isNotBlank();
		assertThat(response.qrCodeUri()).startsWith("otpauth://totp/Hospital-IAM:clinician%40hospital.org");
		assertThat(response.backupCodes()).hasSize(8);

		ArgumentCaptor<UserMfa> captor = ArgumentCaptor.forClass(UserMfa.class);
		verify(userMfaRepository).save(captor.capture());
		UserMfa saved = captor.getValue();
		assertThat(saved.getUserId()).isEqualTo(testUserId);
		assertThat(saved.isEnabled()).isFalse();
		assertThat(saved.backupCodes()).hasSize(8);
	}

	@Test
	@DisplayName("Should activate MFA with valid TOTP code and reject invalid code")
	void shouldActivateMfa() {
		String secret = TotpGenerator.generateSecret();
		UserMfa mfa = UserMfa.create(testUserId, secret, List.of());
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));

		// Rejects invalid code
		assertThatThrownBy(() -> mfaService.activate(testUserId, "000000"))
				.isInstanceOf(InvalidTotpException.class);
		assertThat(mfa.isEnabled()).isFalse();

		// Accepts valid code
		String validCode = TotpGenerator.generateTotp(secret, Instant.now());
		mfaService.activate(testUserId, validCode);
		assertThat(mfa.isEnabled()).isTrue();
		verify(userMfaRepository).save(mfa);
	}

	@Test
	@DisplayName("Should disable MFA with valid TOTP code or account password")
	void shouldDisableMfa() {
		String secret = TotpGenerator.generateSecret();
		UserMfa mfa = UserMfa.create(testUserId, secret, List.of());
		mfa.activate();

		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

		// Disabling with valid TOTP code
		String validCode = TotpGenerator.generateTotp(secret, Instant.now());
		mfaService.disable(testUserId, validCode);
		assertThat(mfa.isEnabled()).isFalse();

		// Re-enabling for password test
		mfa.activate();
		when(passwordEncoder.matches("P@ssw0rd123!", testUser.getPasswordHash())).thenReturn(true);
		mfaService.disable(testUserId, "P@ssw0rd123!");
		assertThat(mfa.isEnabled()).isFalse();

		// Rejects wrong code/password
		mfa.activate();
		when(passwordEncoder.matches("wrong-password", testUser.getPasswordHash())).thenReturn(false);
		assertThatThrownBy(() -> mfaService.disable(testUserId, "wrong-password"))
				.isInstanceOf(AuthenticationException.class);
	}

	@Test
	@DisplayName("Should return MFA status correctly")
	void shouldGetStatus() {
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
		MfaStatusResponse statusNone = mfaService.getStatus(testUserId);
		assertThat(statusNone.enabled()).isFalse();

		UserMfa mfa = UserMfa.create(testUserId, "SECRET", List.of());
		mfa.activate();
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));
		MfaStatusResponse statusActive = mfaService.getStatus(testUserId);
		assertThat(statusActive.enabled()).isTrue();
		assertThat(statusActive.optionalEnrolledAt()).isPresent();
	}

	@Test
	@DisplayName("Should verify login challenge with TOTP code and issue tokens")
	void shouldVerifyLoginWithTotp() {
		TenantId tenantId = TenantId.generate();
		String mfaToken = "mfa-challenge-ticket";
		String secret = TotpGenerator.generateSecret();
		UserMfa mfa = UserMfa.create(testUserId, secret, List.of());
		mfa.activate();

		MfaChallengeClaims claims = new MfaChallengeClaims(testUserId, tenantId, Instant.now(), Instant.now().plusSeconds(300));
		when(tokenProvider.parseMfaChallengeToken(mfaToken)).thenReturn(claims);
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));

		EffectiveAccess access = new EffectiveAccess(
				testUserId, testUser.getEmail(), tenantId, false, false,
				Set.of(tenantId), Set.of(), Set.of("DOCTOR"), Set.of("patient:read"), Set.of(), Set.of("/clinic/"));
		when(effectiveAccessResolver.resolve(testUser, tenantId)).thenReturn(access);
		when(tokenProvider.createAccessToken(eq(access), any())).thenReturn("access-token-123");
		when(tokenProvider.createRefreshToken(testUserId, tenantId)).thenReturn("refresh-token-456");
		when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

		String validCode = TotpGenerator.generateTotp(secret, Instant.now());
		TokenResponse response = mfaService.verifyLogin(MfaLoginVerifyCommand.of(mfaToken, validCode));

		assertThat(response.mfaRequired()).isFalse();
		assertThat(response.accessToken()).isEqualTo("access-token-123");
		assertThat(response.refreshToken()).isEqualTo("refresh-token-456");
		assertThat(response.user().email()).isEqualTo("clinician@hospital.org");
	}

	@Test
	@DisplayName("Should verify login challenge with single-use backup recovery code and consume it")
	void shouldVerifyLoginWithBackupCode() {
		TenantId tenantId = TenantId.generate();
		String mfaToken = "mfa-challenge-ticket";
		String backupCode = "ABCD-1234";
		String backupHash = TotpGenerator.hashBackupCode(backupCode);

		UserMfa mfa = UserMfa.create(testUserId, "SECRET", List.of(backupHash));
		mfa.activate();

		MfaChallengeClaims claims = new MfaChallengeClaims(testUserId, tenantId, Instant.now(), Instant.now().plusSeconds(300));
		when(tokenProvider.parseMfaChallengeToken(mfaToken)).thenReturn(claims);
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));

		EffectiveAccess access = new EffectiveAccess(
				testUserId, testUser.getEmail(), tenantId, false, false,
				Set.of(tenantId), Set.of(), Set.of("DOCTOR"), Set.of("patient:read"), Set.of(), Set.of("/clinic/"));
		when(effectiveAccessResolver.resolve(testUser, tenantId)).thenReturn(access);
		when(tokenProvider.createAccessToken(eq(access), any())).thenReturn("access-token-123");
		when(tokenProvider.createRefreshToken(testUserId, tenantId)).thenReturn("refresh-token-456");

		TokenResponse response = mfaService.verifyLogin(MfaLoginVerifyCommand.of(mfaToken, backupCode));

		assertThat(response.accessToken()).isEqualTo("access-token-123");
		assertThat(mfa.backupCodes()).isEmpty(); // Backup code was consumed!
		verify(userMfaRepository).save(mfa);
	}

	@Test
	@DisplayName("Should reject invalid code during MFA login challenge")
	void shouldRejectInvalidCodeOnLogin() {
		String mfaToken = "mfa-challenge-ticket";
		UserMfa mfa = UserMfa.create(testUserId, "SECRET", List.of("some-other-hash"));
		mfa.activate();

		MfaChallengeClaims claims = new MfaChallengeClaims(testUserId, null, Instant.now(), Instant.now().plusSeconds(300));
		when(tokenProvider.parseMfaChallengeToken(mfaToken)).thenReturn(claims);
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));

		assertThatThrownBy(() -> mfaService.verifyLogin(MfaLoginVerifyCommand.of(mfaToken, "999999")))
				.isInstanceOf(InvalidTotpException.class);
	}

	@Test
	@DisplayName("Should reject initiating setup for inactive or suspended user")
	void shouldRejectInitiatingSetupForInactiveUser() {
		User inactiveUser = new User(testUserId, "inactive@clinic.org", "hash", "Inactive", UserStatus.SUSPENDED, false, Instant.now(), Instant.now());
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(inactiveUser));

		assertThatThrownBy(() -> mfaService.initiateSetup(testUserId, "test"))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Cannot configure MFA");
	}

	@Test
	@DisplayName("Should reset existing MFA configuration when initiateSetup is called again")
	void shouldResetExistingMfaOnInitiateSetup() {
		UserMfa existingMfa = UserMfa.create(testUserId, "OLD_SECRET", List.of("old-hash"));
		existingMfa.activate();

		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(existingMfa));

		MfaSetupResponse response = mfaService.initiateSetup(testUserId, null);

		assertThat(response.secret()).isNotEqualTo("OLD_SECRET");
		assertThat(response.qrCodeUri()).contains("ed-iam"); // default issuer
		assertThat(existingMfa.getSecret()).isEqualTo(response.secret());
		assertThat(existingMfa.isEnabled()).isFalse(); // reset disables it until activated
		verify(userMfaRepository).save(existingMfa);
	}

	@Test
	@DisplayName("Should fail activation with invalid TOTP code")
	void shouldFailActivationWithInvalidCode() {
		UserMfa mfa = UserMfa.create(testUserId, "SECRET_KEY", List.of());
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));

		assertThatThrownBy(() -> mfaService.activate(testUserId, "000000"))
				.isInstanceOf(InvalidTotpException.class);
		assertThat(mfa.isEnabled()).isFalse();
	}

	@Test
	@DisplayName("Should return silently when disabling MFA for user without MFA")
	void shouldReturnSilentlyWhenDisablingWithoutMfa() {
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
		mfaService.disable(testUserId, "any-code");

		UserMfa disabledMfa = UserMfa.create(testUserId, "SECRET", List.of());
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(disabledMfa));
		mfaService.disable(testUserId, "any-code");
	}

	@Test
	@DisplayName("Should return disabled status when userMfaRepository is null")
	void shouldReturnDisabledStatusWhenRepositoryIsNull() {
		MfaService serviceWithoutRepo = new MfaService(null, userRepository, passwordEncoder, tokenProvider, effectiveAccessResolver);
		MfaStatusResponse status = serviceWithoutRepo.getStatus(testUserId);
		assertThat(status.enabled()).isFalse();
		assertThat(status.enrolledAt()).isNull();
	}

	@Test
	@DisplayName("Should reject login verification if user is suspended or deactivated or MFA is not enabled")
	void shouldRejectLoginVerificationForAbnormalStates() {
		String mfaToken = "mfa-token";
		MfaChallengeClaims claims = new MfaChallengeClaims(testUserId, null, Instant.now(), Instant.now().plusSeconds(300));
		when(tokenProvider.parseMfaChallengeToken(mfaToken)).thenReturn(claims);

		// Suspended
		User suspendedUser = new User(testUserId, "u@c.org", "h", "U", UserStatus.SUSPENDED, false, Instant.now(), Instant.now());
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(suspendedUser));
		assertThatThrownBy(() -> mfaService.verifyLogin(MfaLoginVerifyCommand.of(mfaToken, "123456")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("suspended");

		// Deactivated
		User deactivatedUser = new User(testUserId, "u@c.org", "h", "U", UserStatus.DEACTIVATED, false, Instant.now(), Instant.now());
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(deactivatedUser));
		assertThatThrownBy(() -> mfaService.verifyLogin(MfaLoginVerifyCommand.of(mfaToken, "123456")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("deactivated");

		// Active user but MFA not enabled
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		UserMfa inactiveMfa = UserMfa.create(testUserId, "SEC", List.of());
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(inactiveMfa));
		assertThatThrownBy(() -> mfaService.verifyLogin(MfaLoginVerifyCommand.of(mfaToken, "123456")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("not enabled");
	}

	@Test
	@DisplayName("Should enforce session limits and revoke oldest session during MFA verification")
	void shouldEnforceSessionLimitsDuringMfaVerification() {
		SessionRegistryPort sessionRegistry = org.mockito.Mockito.mock(SessionRegistryPort.class);
		TokenRevocationPort tokenRevocationPort = org.mockito.Mockito.mock(TokenRevocationPort.class);
		SessionProperties sessionProps = new SessionProperties(1, SessionProperties.SessionLimitStrategy.TERMINATE_OLDEST, 3, 900L);

		MfaService serviceWithSessions = new MfaService(
				userMfaRepository, userRepository, passwordEncoder, tokenProvider, effectiveAccessResolver,
				sessionRegistry, tokenRevocationPort, sessionProps);

		String mfaToken = "mfa-token-session";
		String secret = TotpGenerator.generateSecret();
		UserMfa mfa = UserMfa.create(testUserId, secret, List.of());
		mfa.activate();

		MfaChallengeClaims claims = new MfaChallengeClaims(testUserId, null, Instant.now(), Instant.now().plusSeconds(300));
		when(tokenProvider.parseMfaChallengeToken(mfaToken)).thenReturn(claims);
		when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
		when(userMfaRepository.findByUserId(testUserId)).thenReturn(Optional.of(mfa));

		EffectiveAccess access = new EffectiveAccess(
				testUserId, testUser.getEmail(), null, false, false,
				Set.of(), Set.of(), Set.of("USER"), Set.of(), Set.of(), Set.of("/"));
		when(effectiveAccessResolver.resolve(testUser, null)).thenReturn(access);
		when(tokenProvider.createAccessToken(eq(access), any())).thenReturn("access-token-new");
		when(tokenProvider.createRefreshToken(testUserId, null)).thenReturn("refresh-token-new");
		when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

		UserSession oldSession = UserSession.create(testUserId, null, "old-token-id", 3600, "127.0.0.1", "curl");
		when(sessionRegistry.findActiveSessions(testUserId, null)).thenReturn(List.of(oldSession));

		String validTotp = TotpGenerator.generateTotp(secret, Instant.now());
		TokenResponse response = serviceWithSessions.verifyLogin(MfaLoginVerifyCommand.of(mfaToken, validTotp, "127.0.0.1", "curl"));

		assertThat(response.accessToken()).isEqualTo("access-token-new");
		verify(sessionRegistry).revokeSession(oldSession.id());
		verify(tokenRevocationPort).revokeToken(eq("old-token-id"), any());
		verify(sessionRegistry).registerSession(any(UserSession.class));
	}
}
