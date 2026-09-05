package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.UpdateScopeNodeCommand;
import io.github.edmaputra.iam.domain.auth.ApiKeyAuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.OidcAuthCredentials;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying record compact constructor null-guards and collection defaults.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class DomainAndModelInvariantsTest {

	@Test
	@DisplayName("Should enforce non-null invariants and default collections on SecurityContextCurrentActor")
	void shouldVerifySecurityContextCurrentActorInvariants() {
		UUID userId = UUID.randomUUID();

		assertThatThrownBy(() -> new SecurityContextCurrentActor(null, "test@org", null, false, false, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new SecurityContextCurrentActor(userId, null, null, false, false, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);

		// Passing null collections should safely default to empty immutable sets
		SecurityContextCurrentActor actor = new SecurityContextCurrentActor(
				userId, "test@org", null, false, false, null, null, null, null, null);

		assertThat(actor.groups()).isEmpty();
		assertThat(actor.roles()).isEmpty();
		assertThat(actor.permissions()).isEmpty();
		assertThat(actor.accessibleScopeNodeIds()).isEmpty();
		assertThat(actor.accessibleScopePaths()).isEmpty();
	}

	@Test
	@DisplayName("Should enforce non-null invariants and default collections on EffectiveAccess")
	void shouldVerifyEffectiveAccessInvariants() {
		UserId userId = UserId.generate();

		assertThatThrownBy(() -> new EffectiveAccess(null, "test@org", null, false, false, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new EffectiveAccess(userId, null, null, false, false, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);

		EffectiveAccess access = new EffectiveAccess(
				userId, "test@org", null, false, false, null, null, null, null, null);

		assertThat(access.groups()).isEmpty();
		assertThat(access.roles()).isEmpty();
		assertThat(access.permissions()).isEmpty();
		assertThat(access.accessibleScopeNodeIds()).isEmpty();
		assertThat(access.accessibleScopePaths()).isEmpty();
	}

	@Test
	@DisplayName("Should enforce non-null invariants and default collections on UserProfileResponse")
	void shouldVerifyUserProfileResponseInvariants() {
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> new UserProfileResponse(null, "test@org", "Name", null, false, false, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new UserProfileResponse(id, null, "Name", null, false, false, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new UserProfileResponse(id, "test@org", null, null, false, false, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);

		UserProfileResponse response = new UserProfileResponse(
				id, "test@org", "Name", null, false, false, null, null, null, null, null);

		assertThat(response.groups()).isEmpty();
		assertThat(response.roles()).isEmpty();
		assertThat(response.permissions()).isEmpty();
		assertThat(response.accessibleScopeNodeIds()).isEmpty();
		assertThat(response.accessibleScopePaths()).isEmpty();
	}

	@Test
	@DisplayName("Should enforce validation on OidcAuthCredentials")
	void shouldVerifyOidcAuthCredentialsInvariants() {
		assertThatThrownBy(() -> new OidcAuthCredentials(null, null, "sub", "Name", null, null, null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new OidcAuthCredentials(null, "   ", "sub", "Name", null, null, null))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new OidcAuthCredentials(null, "test@org", null, "Name", null, null, null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new OidcAuthCredentials(null, "test@org", "   ", "Name", null, null, null))
				.isInstanceOf(IllegalArgumentException.class);

		// Missing/blank fullName defaults to email; null externalGroups defaults to empty list
		OidcAuthCredentials creds = new OidcAuthCredentials(null, "test@org", "sub", null, null, null, null);
		assertThat(creds.fullName()).isEqualTo("test@org");
		assertThat(creds.externalGroups()).isEmpty();
	}

	@Test
	@DisplayName("Should enforce validation on PasswordAuthCredentials")
	void shouldVerifyPasswordAuthCredentialsInvariants() {
		assertThatThrownBy(() -> new PasswordAuthCredentials(null, "pass"))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new PasswordAuthCredentials("   ", "pass"))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new PasswordAuthCredentials("test@org", null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new PasswordAuthCredentials("test@org", "   "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should enforce validation on ApiKeyAuthCredentials")
	void shouldVerifyApiKeyAuthCredentialsInvariants() {
		assertThatThrownBy(() -> new ApiKeyAuthCredentials(null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new ApiKeyAuthCredentials("   "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should enforce validation on Commands and DTOs")
	void shouldVerifyCommandsAndDtos() {
		TenantId tenantId = TenantId.generate();
		ScopeNodeId nodeId = ScopeNodeId.generate();

		assertThatThrownBy(() -> new CreateScopeNodeCommand(tenantId, null, "   ", "Name"))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new CreateScopeNodeCommand(tenantId, null, "CODE", "   "))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new UpdateScopeNodeCommand(tenantId, nodeId, "   ", "Name"))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new UpdateScopeNodeCommand(tenantId, nodeId, "CODE", "   "))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new LoginCommand("   ", "pass", tenantId))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new LoginCommand("email@org", "   ", tenantId))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new TokenResponse(null, "refresh", "Bearer", 3600, null))
				.isInstanceOf(NullPointerException.class);

		// test tokenType fallback to Bearer when null or blank
		TokenResponse tokenResp = new TokenResponse("access", "refresh", null, 3600, new UserProfileResponse(
				UUID.randomUUID(), "a@b.com", "Name", null, false, false, null, null, null, null, null));
		assertThat(tokenResp.tokenType()).isEqualTo("Bearer");

		assertThatThrownBy(() -> new OperationContext(null, "corr"))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new OperationContext("   ", "corr"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should enforce provider router invariants and dispatch exception on unsupported credentials")
	void shouldVerifyAuthenticationProviderRouterInvariants() {
		assertThatThrownBy(() -> new io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter(List.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("At least one AuthenticationProvider must be configured.");

		io.github.edmaputra.iam.application.port.out.AuthenticationProvider mockProvider =
				new io.github.edmaputra.iam.application.port.out.AuthenticationProvider() {
					@Override public boolean supports(io.github.edmaputra.iam.domain.auth.AuthCredentialType credentialType) { return false; }
					@Override public AuthenticatedIdentity authenticate(io.github.edmaputra.iam.domain.auth.AuthCredentials credentials) { return null; }
				};

		io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter router =
				new io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter(List.of(mockProvider));

		assertThatThrownBy(() -> router.authenticate(new ApiKeyAuthCredentials("key")))
				.isInstanceOf(io.github.edmaputra.iam.domain.exception.AuthenticationException.class)
				.hasMessageContaining("No authentication provider registered");
	}

}
