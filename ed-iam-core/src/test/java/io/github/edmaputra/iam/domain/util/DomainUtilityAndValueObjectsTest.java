package io.github.edmaputra.iam.domain.util;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.service.ScopeSubtreeResolver;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying value object factories, UuidV7 extraction, and CurrentActor defaults.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class DomainUtilityAndValueObjectsTest {

	@Test
	@DisplayName("Should validate TenantId.from input")
	void shouldValidateTenantIdFrom() {
		UUID uuid = UUID.randomUUID();
		TenantId tenantId = TenantId.from(uuid.toString());
		assertThat(tenantId.value()).isEqualTo(uuid);

		assertThatThrownBy(() -> TenantId.from(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be blank");

		assertThatThrownBy(() -> TenantId.from("   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be blank");
	}

	@Test
	@DisplayName("Should validate ScopeNodeId.from input")
	void shouldValidateScopeNodeIdFrom() {
		UUID uuid = UUID.randomUUID();
		ScopeNodeId scopeNodeId = ScopeNodeId.from(uuid.toString());
		assertThat(scopeNodeId.value()).isEqualTo(uuid);

		assertThatThrownBy(() -> ScopeNodeId.from(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be blank");

		assertThatThrownBy(() -> ScopeNodeId.from("   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be blank");
	}

	@Test
	@DisplayName("Should validate UuidV7.extractTimestamp logic and version check")
	void shouldValidateUuidV7ExtractTimestamp() {
		Instant now = Instant.now();
		UUID uuid7 = UuidV7.generate(now);

		Instant extracted = UuidV7.extractTimestamp(uuid7);
		assertThat(extracted.toEpochMilli()).isEqualTo(now.toEpochMilli());

		assertThatThrownBy(() -> UuidV7.extractTimestamp(null))
				.isInstanceOf(NullPointerException.class);

		UUID uuid4 = UUID.randomUUID();
		assertThatThrownBy(() -> UuidV7.extractTimestamp(uuid4))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Expected UUID version 7 but found version 4");
	}

	@Test
	@DisplayName("Should test ScopeSubtreeResolver.isPathAccessible boundary conditions")
	void shouldTestIsPathAccessibleBoundaries() {
		assertThat(ScopeSubtreeResolver.isPathAccessible(null, "/t1/node1/")).isFalse();
		assertThat(ScopeSubtreeResolver.isPathAccessible(Set.of(), "/t1/node1/")).isFalse();
		assertThat(ScopeSubtreeResolver.isPathAccessible(Set.of("/t1/"), null)).isFalse();
		assertThat(ScopeSubtreeResolver.isPathAccessible(Set.of("/t1/"), "   ")).isFalse();
		assertThat(ScopeSubtreeResolver.isPathAccessible(Set.of("/t1/parent/"), "/t1/parent/child/")).isTrue();
	}

	@Test
	@DisplayName("Should test CurrentActor default method boundaries")
	void shouldTestCurrentActorDefaultMethods() {
		UUID userId = UUID.randomUUID();
		UUID targetScope = UUID.randomUUID();

		CurrentActor actor = new CurrentActor() {
			@Override public UUID userId() { return userId; }
			@Override public String email() { return "test@org"; }
			@Override public UUID tenantId() { return null; }
			@Override public boolean isPlatformSuperAdmin() { return false; }
			@Override public boolean isTenantWide() { return false; }
			@Override public Set<String> groups() { return Set.of(); }
			@Override public Set<String> roles() { return Set.of(); }
			@Override public Set<String> permissions() { return null; }
			@Override public Set<UUID> accessibleScopeNodeIds() { return null; }
		};

		assertThat(actor.hasPermission("READ")).isFalse();
		assertThat(actor.canAccessScope(null)).isFalse();
		assertThat(actor.canAccessScope(targetScope)).isFalse();
	}
}
