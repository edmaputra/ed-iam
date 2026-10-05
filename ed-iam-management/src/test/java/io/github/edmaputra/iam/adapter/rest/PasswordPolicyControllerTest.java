package io.github.edmaputra.iam.adapter.rest;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.PasswordPolicyDtos.PasswordPolicyResponse;
import io.github.edmaputra.iam.adapter.rest.dto.PasswordPolicyDtos.UpdatePasswordPolicyRequest;
import io.github.edmaputra.iam.application.port.in.ManagePasswordPolicyUseCase;
import io.github.edmaputra.iam.application.port.in.UpdatePasswordPolicyCommand;
import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordPolicyController}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class PasswordPolicyControllerTest {

	private ManagePasswordPolicyUseCase managePasswordPolicyUseCase;
	private PasswordPolicyController controller;

	@BeforeEach
	void setUp() {
		managePasswordPolicyUseCase = mock(ManagePasswordPolicyUseCase.class);
		controller = new PasswordPolicyController(managePasswordPolicyUseCase);
	}

	@Test
	@DisplayName("GET /api/v1/password-policy returns policy for tenant")
	void getPolicyReturnsPolicy() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		PasswordPolicy policy = new PasswordPolicy(12, 100, 1, 1, 1, 1, null, null, true);
		when(managePasswordPolicyUseCase.getPolicy(tenantId)).thenReturn(policy);

		ResponseEntity<PasswordPolicyResponse> response = controller.getPolicy(tenantUuid.toString(), null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().tenantId()).isEqualTo(tenantUuid);
		assertThat(response.getBody().minLength()).isEqualTo(12);
	}

	@Test
	@DisplayName("PUT /api/v1/password-policy updates and returns updated policy")
	void updatePolicyUpdatesAndReturns() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		PasswordPolicy current = PasswordPolicy.defaultPolicy();
		PasswordPolicy updated = new PasswordPolicy(16, 128, 2, 2, 2, 2, "^[a-zA-Z0-9]+$", "Alphanumeric", false);

		when(managePasswordPolicyUseCase.getPolicy(tenantId)).thenReturn(current);
		when(managePasswordPolicyUseCase.updatePolicy(any(UpdatePasswordPolicyCommand.class))).thenReturn(updated);

		UpdatePasswordPolicyRequest request = new UpdatePasswordPolicyRequest(
				tenantUuid,
				16,
				128,
				2,
				2,
				2,
				2,
				"^[a-zA-Z0-9]+$",
				"Alphanumeric",
				false
		);

		ResponseEntity<PasswordPolicyResponse> response = controller.updatePolicy(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().minLength()).isEqualTo(16);
		assertThat(response.getBody().minUppercase()).isEqualTo(2);
		assertThat(response.getBody().disallowUsername()).isFalse();

		verify(managePasswordPolicyUseCase).updatePolicy(any(UpdatePasswordPolicyCommand.class));
	}
}
