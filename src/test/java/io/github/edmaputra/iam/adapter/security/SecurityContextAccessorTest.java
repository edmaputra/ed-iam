package io.github.edmaputra.iam.adapter.security;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.security.CurrentActor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextAccessorTest {

	private final SecurityContextAccessor accessor = new SecurityContextAccessor();

	@Test
	@DisplayName("Should be empty when no actor is bound in scope")
	void shouldBeEmptyWhenNotBound() {
		assertThat(accessor.currentActor()).isEmpty();
		assertThatThrownBy(accessor::requireCurrentActor)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("authenticated actor context is required");
	}

	@Test
	@DisplayName("Should expose actor within callWithActor scope and clear after completion")
	void shouldExposeActorInScope() throws Exception {
		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(),
				"user@hospital.org",
				UUID.randomUUID(),
				false,
				true,
				Set.of("ADMIN"),
				Set.of("ROLE_ADMIN"),
				Set.of("ALL"),
				Set.of(),
				Set.of());

		String result = accessor.callWithActor(actor, () -> {
			assertThat(accessor.currentActor()).isPresent();
			assertThat(accessor.requireCurrentActor().email()).isEqualTo("user@hospital.org");
			return "success";
		});

		assertThat(result).isEqualTo("success");
		assertThat(accessor.currentActor()).isEmpty();
	}
}
