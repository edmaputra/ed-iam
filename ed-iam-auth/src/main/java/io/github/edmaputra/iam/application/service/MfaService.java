package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.edmaputra.iam.application.model.MfaChallengeClaims;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.application.port.in.MfaLoginVerifyCommand;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.auth.mfa.TotpGenerator;
import io.github.edmaputra.iam.domain.exception.AccountLockedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.InvalidTotpException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Application service implementing {@link ManageMfaUseCase}.
 * Manages TOTP Multi-Factor Authentication enrollment, verification, backup codes, and login challenge resolution.
 *
 * @author edmaputra
 * @since 0.5.0
 */
public class MfaService implements ManageMfaUseCase {

	private static final int BACKUP_CODE_COUNT = 8;
	private static final int TOTP_WINDOW_STEPS = 1;

	private final UserMfaRepository userMfaRepository;
	private final UserRepository userRepository;
	private final PasswordEncoderPort passwordEncoder;
	private final UserTokenService userTokenService;
	private final AuthSessionService authSessionService;
	private final LoginAttemptTrackerPort loginAttemptTracker;

	/**
	 * Canonical constructor delegating token, session, and attempt tracking orchestration to collaborator services.
	 */
	public MfaService(
			UserMfaRepository userMfaRepository,
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserTokenService userTokenService,
			AuthSessionService authSessionService,
			LoginAttemptTrackerPort loginAttemptTracker) {
		this.userMfaRepository = userMfaRepository;
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoderPort must not be null.");
		this.userTokenService = Objects.requireNonNull(userTokenService, "UserTokenService must not be null.");
		this.authSessionService = authSessionService;
		this.loginAttemptTracker = loginAttemptTracker;
	}

	/**
	 * Convenience constructor without login attempt tracking.
	 */
	public MfaService(
			UserMfaRepository userMfaRepository,
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserTokenService userTokenService,
			AuthSessionService authSessionService) {
		this(userMfaRepository, userRepository, passwordEncoder, userTokenService, authSessionService, null);
	}

	/**
	 * Convenience constructor without session management or login attempt tracking.
	 */
	public MfaService(
			UserMfaRepository userMfaRepository,
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserTokenService userTokenService) {
		this(userMfaRepository, userRepository, passwordEncoder, userTokenService, null, null);
	}

	@Override
	public MfaSetupResponse initiateSetup(UserId userId, String issuer) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		requireUserMfaRepository();

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException(userId));

		if (!user.isActive()) {
			throw new AuthenticationException("Cannot configure MFA for inactive or suspended user.");
		}

		String secret = TotpGenerator.generateSecret();
		List<String> plainBackupCodes = TotpGenerator.generateBackupCodes(BACKUP_CODE_COUNT);
		List<String> hashedBackupCodes = plainBackupCodes.stream()
				.map(TotpGenerator::hashBackupCode)
				.toList();

		Optional<UserMfa> existingMfa = userMfaRepository.findByUserId(userId);
		UserMfa mfa;
		if (existingMfa.isPresent()) {
			mfa = existingMfa.get();
			mfa.resetSecret(secret, hashedBackupCodes);
		}
		else {
			mfa = UserMfa.create(userId, secret, hashedBackupCodes);
		}
		userMfaRepository.save(mfa);

		String effectiveIssuer = (issuer != null && !issuer.isBlank()) ? issuer : "ed-iam";
		String qrCodeUri = TotpGenerator.generateOtpAuthUri(effectiveIssuer, user.getEmail(), secret);

		return new MfaSetupResponse(secret, qrCodeUri, plainBackupCodes);
	}

	@Override
	public void activate(UserId userId, String code) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		requireUserMfaRepository();

		UserMfa mfa = userMfaRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalStateException("MFA setup has not been initiated for user: " + userId.value()));

		boolean valid = TotpGenerator.verifyTotp(mfa.getSecret(), code, Instant.now(), TOTP_WINDOW_STEPS);
		if (!valid) {
			throw new InvalidTotpException("Invalid TOTP verification code.");
		}

		mfa.activate();
		userMfaRepository.save(mfa);
	}

	@Override
	public void disable(UserId userId, String codeOrPassword) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(codeOrPassword, "Code or password must not be null.");
		requireUserMfaRepository();

		Optional<UserMfa> mfaOpt = userMfaRepository.findByUserId(userId);
		if (mfaOpt.isEmpty() || !mfaOpt.get().isEnabled()) {
			return;
		}

		UserMfa mfa = mfaOpt.get();
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException(userId));

		boolean totpMatches = TotpGenerator.verifyTotp(mfa.getSecret(), codeOrPassword, Instant.now(), TOTP_WINDOW_STEPS);
		boolean passwordMatches = user.optionalPasswordHash().isPresent()
				&& passwordEncoder.matches(codeOrPassword, user.getPasswordHash());

		if (!totpMatches && !passwordMatches) {
			throw new AuthenticationException("Invalid verification code or password to disable MFA.");
		}

		mfa.disable();
		userMfaRepository.save(mfa);
	}

	@Override
	public MfaStatusResponse getStatus(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		if (userMfaRepository == null) {
			return new MfaStatusResponse(false, null);
		}

		Optional<UserMfa> mfaOpt = userMfaRepository.findByUserId(userId);
		boolean enabled = mfaOpt.map(UserMfa::isEnabled).orElse(false);
		Instant enrolledAt = enabled ? mfaOpt.map(UserMfa::getUpdatedAt).orElse(null) : null;
		return new MfaStatusResponse(enabled, enrolledAt);
	}

	@Override
	public TokenResponse verifyLogin(MfaLoginVerifyCommand command) {
		Objects.requireNonNull(command, "MfaLoginVerifyCommand must not be null.");
		requireUserMfaRepository();

		MfaChallengeClaims claims = userTokenService.parseMfaChallengeToken(command.mfaToken());
		User user = userRepository.findById(claims.userId())
				.orElseThrow(() -> new UserNotFoundException(claims.userId()));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		if (loginAttemptTracker != null) {
			LockoutStatus status = loginAttemptTracker.getLockoutStatus(user.getEmail());
			if (status != null && status.locked()) {
				throw new AccountLockedException(
						"Account is temporarily locked due to too many failed attempts until: " + status.lockedUntil(),
						status.lockedUntil());
			}
		}

		UserMfa mfa = userMfaRepository.findByUserId(claims.userId())
				.orElseThrow(() -> new AuthenticationException("MFA configuration not found for user."));

		if (!mfa.isEnabled()) {
			throw new AuthenticationException("MFA is not enabled for this user account.");
		}

		boolean verified = false;
		OptionalLong verifiedStep = TotpGenerator.verifyTotpStep(mfa.getSecret(), command.code(), Instant.now(), TOTP_WINDOW_STEPS, mfa.getLastVerifiedStep());
		if (verifiedStep.isPresent()) {
			mfa.markStepVerified(verifiedStep.getAsLong());
			userMfaRepository.save(mfa);
			verified = true;
		}
		else {
			String hashedBackupCode = TotpGenerator.hashBackupCode(command.code());
			boolean backupConsumed = mfa.consumeBackupCode(hashedBackupCode);
			if (backupConsumed) {
				userMfaRepository.save(mfa);
				verified = true;
			}
		}

		if (!verified) {
			if (loginAttemptTracker != null) {
				loginAttemptTracker.recordFailedAttempt(user.getEmail(), Instant.now());
			}
			throw new InvalidTotpException("Invalid TOTP code or backup recovery code.");
		}

		if (loginAttemptTracker != null) {
			loginAttemptTracker.recordSuccessfulAttempt(user.getEmail());
		}

		UserTokenService.TokenIssueResult issueResult = userTokenService.issueTokens(user, claims.tenantId());

		if (authSessionService != null) {
			authSessionService.registerSession(
					user.getId(),
					issueResult.effectiveAccess().tenantId(),
					issueResult.tokenId(),
					userTokenService.getAccessTokenExpirationSeconds(),
					command.ipAddress(),
					command.userAgent());
		}

		return TokenResponse.of(
				issueResult.accessToken(),
				issueResult.refreshToken(),
				userTokenService.getAccessTokenExpirationSeconds(),
				issueResult.profile());
	}

	@Override
	public void resetMfa(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		requireUserMfaRepository();

		userMfaRepository.deleteByUserId(userId);
		if (authSessionService != null) {
			authSessionService.logoutAll(userId, null);
		}
	}

	private void requireUserMfaRepository() {
		if (userMfaRepository == null) {
			throw new IllegalStateException("UserMfaRepository bean is not available in the current context.");
		}
	}
}
