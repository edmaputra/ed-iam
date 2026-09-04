package io.github.edmaputra.iam.application.service;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.adapter.security.jwt.JwtProperties;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

	private AuthenticationProviderRouter authRouter;
	private UserRepository userRepository;
	private EffectiveAccessResolver effectiveAccessResolver;
	private JwtTokenProvider jwtTokenProvider;
	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		authRouter = Mockito.mock(AuthenticationProviderRouter.class);
		userRepository = Mockito.mock(UserRepository.class);
		effectiveAccessResolver = Mockito.mock(EffectiveAccessResolver.class);
		jwtTokenProvider = new JwtTokenProvider(JwtProperties.defaultProperties());

		service = new AuthenticationService(authRouter, userRepository, effectiveAccessResolver, jwtTokenProvider);
	}

	@Test
	@DisplayName("Should login successfully and return access and refresh tokens")
	void shouldLoginSuccessfully() {
		User user = User.create("doctor@hospital.org", "hash", "Dr. Doctor", false);
		TenantId tenantId = TenantId.generate();

		AuthenticatedIdentity identity = new AuthenticatedIdentity(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				false,
				ProviderType.LOCAL);

		EffectiveAccess access = new EffectiveAccess(
				user.getId(),
				user.getEmail(),
				tenantId,
				false,
				false,
				Set.of("SURGERY"),
				Set.of("SURGEON"),
				Set.of("NOTE_WRITE"),
				Set.of(),
				Set.of("/t1/"));

		when(authRouter.authenticate(any())).thenReturn(identity);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, tenantId)).thenReturn(access);

		TokenResponse response = service.login(LoginCommand.of("doctor@hospital.org", "secret", tenantId));

		assertThat(response).isNotNull();
		assertThat(response.accessToken()).isNotBlank();
		assertThat(response.refreshToken()).isNotBlank();
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.user().email()).isEqualTo("doctor@hospital.org");
		assertThat(response.user().roles()).containsExactly("SURGEON");
	}

	@Test
	@DisplayName("Should refresh token successfully")
	void shouldRefreshTokenSuccessfully() {
		User user = User.create("doctor@hospital.org", "hash", "Dr. Doctor", false);
		TenantId tenantId = TenantId.generate();

		String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), tenantId);

		EffectiveAccess access = new EffectiveAccess(
				user.getId(),
				user.getEmail(),
				tenantId,
				false,
				false,
				Set.of("SURGERY"),
				Set.of("SURGEON"),
				Set.of("NOTE_WRITE"),
				Set.of(),
				Set.of("/t1/"));

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, tenantId)).thenReturn(access);

		TokenResponse response = service.refreshToken(new RefreshTokenCommand(refreshToken));

		assertThat(response).isNotNull();
		assertThat(response.accessToken()).isNotBlank();
		assertThat(response.refreshToken()).isNotBlank();
		assertThat(response.user().email()).isEqualTo("doctor@hospital.org");
	}

	@Test
	@DisplayName("Should return me profile for current actor")
	void shouldReturnMeProfile() {
		User user = User.create("admin@hospital.org", "hash", "Hospital Admin", false);
		TenantId tenantId = TenantId.generate();

		SecurityContextCurrentActor actor = new SecurityContextCurrentActor(
				user.getId().value(),
				user.getEmail(),
				tenantId.value(),
				false,
				true,
				Set.of("ADMINS"),
				Set.of("ADMIN"),
				Set.of("ALL"),
				Set.of(),
				Set.of());

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		UserProfileResponse profile = service.getMe(actor);

		assertThat(profile.email()).isEqualTo("admin@hospital.org");
		assertThat(profile.fullName()).isEqualTo("Hospital Admin");
		assertThat(profile.roles()).containsExactly("ADMIN");
	}
}
