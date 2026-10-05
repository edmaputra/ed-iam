package io.github.edmaputra.iam.adapter.rest;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.RedirectUriDtos.AddRedirectUriRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RedirectUriDtos.RedirectUriPolicyResponse;
import io.github.edmaputra.iam.adapter.rest.dto.RedirectUriDtos.UpdateRedirectUriPolicyRequest;
import io.github.edmaputra.iam.application.port.in.ManageRedirectUriUseCase;
import io.github.edmaputra.iam.application.port.in.UpdateRedirectUriPolicyCommand;
import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedirectUriController}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class RedirectUriControllerTest {

	private ManageRedirectUriUseCase manageRedirectUriUseCase;
	private RedirectUriController controller;

	@BeforeEach
	void setUp() {
		manageRedirectUriUseCase = mock(ManageRedirectUriUseCase.class);
		controller = new RedirectUriController(manageRedirectUriUseCase);
	}

	@Test
	@DisplayName("GET /api/v1/redirect-uris returns policy for tenant")
	void getPolicyReturnsPolicy() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		RedirectUriPolicy policy = RedirectUriPolicy.of("https://portal.clinic.org/callback");
		when(manageRedirectUriUseCase.getPolicy(tenantId)).thenReturn(policy);

		ResponseEntity<RedirectUriPolicyResponse> response = controller.getPolicy(tenantUuid.toString(), null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().tenantId()).isEqualTo(tenantUuid);
		assertThat(response.getBody().allowedUris()).containsExactly("https://portal.clinic.org/callback");
	}

	@Test
	@DisplayName("PUT /api/v1/redirect-uris updates and returns updated policy")
	void updatePolicyUpdatesAndReturns() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		RedirectUriPolicy updated = RedirectUriPolicy.of("https://app1.com", "https://app2.com");
		when(manageRedirectUriUseCase.updatePolicy(any(UpdateRedirectUriPolicyCommand.class))).thenReturn(updated);

		UpdateRedirectUriPolicyRequest request = new UpdateRedirectUriPolicyRequest(
				tenantUuid,
				Set.of("https://app1.com", "https://app2.com")
		);

		ResponseEntity<RedirectUriPolicyResponse> response = controller.updatePolicy(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().allowedUris()).containsExactlyInAnyOrder("https://app1.com", "https://app2.com");
		verify(manageRedirectUriUseCase).updatePolicy(any(UpdateRedirectUriPolicyCommand.class));
	}

	@Test
	@DisplayName("POST /api/v1/redirect-uris appends URI and returns updated policy")
	void addUriAppendsAndReturns() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		RedirectUriPolicy updated = RedirectUriPolicy.of("https://new.org");
		when(manageRedirectUriUseCase.addUri(eq(tenantId), eq("https://new.org"))).thenReturn(updated);

		AddRedirectUriRequest request = new AddRedirectUriRequest(tenantUuid, "https://new.org");

		ResponseEntity<RedirectUriPolicyResponse> response = controller.addUri(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().allowedUris()).containsExactly("https://new.org");
	}

	@Test
	@DisplayName("DELETE /api/v1/redirect-uris removes URI and returns updated policy")
	void removeUriDeletesAndReturns() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		RedirectUriPolicy updated = RedirectUriPolicy.empty();
		when(manageRedirectUriUseCase.removeUri(eq(tenantId), eq("https://old.org"))).thenReturn(updated);

		ResponseEntity<RedirectUriPolicyResponse> response = controller.removeUri(null, tenantUuid, "https://old.org");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().allowedUris()).isEmpty();
	}
}
