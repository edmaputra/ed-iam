package io.github.edmaputra.iam.adapter.security.evaluator;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamSecurityEvaluatorTest {

	@Mock
	private CurrentActorProvider currentActorProvider;

	private IamSecurityEvaluator evaluator;

	@BeforeEach
	void setUp() {
		evaluator = new IamSecurityEvaluator(currentActorProvider);
	}

	@Test
	@DisplayName("Should evaluate hasPermission accurately")
	void shouldEvaluateHasPermission() {
		UUID scopeId = UUID.randomUUID();
		CurrentActor actor = new SecurityContextCurrentActor(
				UUID.randomUUID(), "actor@ed-iam.io", UUID.randomUUID(), false, false,
				Set.of("DOCTORS"), Set.of("CHIEF_PHYSICIAN"), Set.of("PATIENT_READ", "PATIENT_WRITE"),
				Set.of(scopeId), Set.of("/hospital/cardiology")
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		assertThat(evaluator.hasPermission("PATIENT_READ")).isTrue();
		assertThat(evaluator.hasPermission("BILLING_DELETE")).isFalse();
		assertThat(evaluator.hasPermission(null)).isFalse();
		assertThat(evaluator.hasPermission("")).isFalse();

		assertThat(evaluator.hasAnyPermission("BILLING_DELETE", "PATIENT_WRITE")).isTrue();
		assertThat(evaluator.hasAnyPermission("BILLING_DELETE", "BILLING_READ")).isFalse();

		assertThat(evaluator.hasAllPermissions("PATIENT_READ", "PATIENT_WRITE")).isTrue();
		assertThat(evaluator.hasAllPermissions("PATIENT_READ", "BILLING_DELETE")).isFalse();

		assertThat(evaluator.canAccessScope(scopeId)).isTrue();
		assertThat(evaluator.canAccessScope(UUID.randomUUID())).isFalse();
		assertThat(evaluator.canAccessScope(null)).isFalse();

		assertThat(evaluator.hasRole("CHIEF_PHYSICIAN")).isTrue();
		assertThat(evaluator.hasRole("NURSE")).isFalse();

		assertThat(evaluator.isInGroup("DOCTORS")).isTrue();
		assertThat(evaluator.isInGroup("ADMINS")).isFalse();

		assertThat(evaluator.isPlatformSuperAdmin()).isFalse();
		assertThat(evaluator.isTenantWide()).isFalse();
	}

	@Test
	@DisplayName("Should grant everything to platform superadmin")
	void shouldGrantAllToSuperadmin() {
		CurrentActor superAdmin = new SecurityContextCurrentActor(
				UUID.randomUUID(), "root@ed-iam.io", null, true, true,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of()
		);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(superAdmin));

		assertThat(evaluator.hasPermission("ANYTHING")).isTrue();
		assertThat(evaluator.hasAnyPermission("ANY_1", "ANY_2")).isTrue();
		assertThat(evaluator.hasAllPermissions("ANY_1", "ANY_2")).isTrue();
		assertThat(evaluator.canAccessScope(UUID.randomUUID())).isTrue();
		assertThat(evaluator.isPlatformSuperAdmin()).isTrue();
		assertThat(evaluator.isTenantWide()).isTrue();
	}

	@Test
	@DisplayName("Should return false when actor is absent")
	void shouldReturnFalseWhenActorAbsent() {
		when(currentActorProvider.currentActor()).thenReturn(Optional.empty());

		assertThat(evaluator.hasPermission("PATIENT_READ")).isFalse();
		assertThat(evaluator.hasAnyPermission("PATIENT_READ")).isFalse();
		assertThat(evaluator.hasAllPermissions("PATIENT_READ")).isFalse();
		assertThat(evaluator.canAccessScope(UUID.randomUUID())).isFalse();
		assertThat(evaluator.isPlatformSuperAdmin()).isFalse();
		assertThat(evaluator.isTenantWide()).isFalse();
		assertThat(evaluator.hasRole("ADMIN")).isFalse();
		assertThat(evaluator.isInGroup("ADMINS")).isFalse();
	}
}
