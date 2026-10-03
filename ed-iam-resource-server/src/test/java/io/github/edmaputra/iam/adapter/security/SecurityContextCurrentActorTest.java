package io.github.edmaputra.iam.adapter.security;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying invariants, defensive collection copying, and contracts on {@link SecurityContextCurrentActor}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class SecurityContextCurrentActorTest {

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
		assertThat(actor.isPlatformSuperAdmin()).isFalse();
		assertThat(actor.isTenantWide()).isFalse();
	}

	@Test
	@DisplayName("Should preserve supplied attributes and collections")
	void shouldPreserveSuppliedAttributes() {
		UUID userId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID scopeId = UUID.randomUUID();

		SecurityContextCurrentActor actor = new SecurityContextCurrentActor(
				userId,
				"admin@org",
				tenantId,
				true,
				true,
				Set.of("ADMINS"),
				Set.of("ROLE_ADMIN"),
				Set.of("iam:user:create"),
				Set.of(scopeId),
				Set.of("/root/"));

		assertThat(actor.userId()).isEqualTo(userId);
		assertThat(actor.email()).isEqualTo("admin@org");
		assertThat(actor.tenantId()).isEqualTo(tenantId);
		assertThat(actor.isPlatformSuperAdmin()).isTrue();
		assertThat(actor.isTenantWide()).isTrue();
		assertThat(actor.groups()).containsExactly("ADMINS");
		assertThat(actor.roles()).containsExactly("ROLE_ADMIN");
		assertThat(actor.permissions()).containsExactly("iam:user:create");
		assertThat(actor.accessibleScopeNodeIds()).containsExactly(scopeId);
		assertThat(actor.accessibleScopePaths()).containsExactly("/root/");
	}
}
