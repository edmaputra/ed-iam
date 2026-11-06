package io.github.edmaputra.iam.adapter.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.CreatePolicyRequest;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyConstraintDto;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyEvaluationRequestDto;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyEvaluationResponseDto;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyResponse;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyTargetDto;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.UpdatePolicyRequest;
import io.github.edmaputra.iam.application.port.in.EvaluatePolicyUseCase;
import io.github.edmaputra.iam.application.port.in.ManagePolicyUseCase;
import io.github.edmaputra.iam.application.port.in.PolicyCommands.CreatePolicyCommand;
import io.github.edmaputra.iam.application.port.in.PolicyCommands.UpdatePolicyCommand;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PolicyController}.
 *
 * @author edmaputra
 * @since 0.14.0
 */
class PolicyControllerTest {

	private ManagePolicyUseCase managePolicyUseCase;
	private CurrentActorProvider currentActorProvider;
	private ObjectProvider<EvaluatePolicyUseCase> policyEvaluatorProvider;
	private EvaluatePolicyUseCase evaluatePolicyUseCase;
	private PolicyController controller;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		managePolicyUseCase = mock(ManagePolicyUseCase.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		policyEvaluatorProvider = mock(ObjectProvider.class);
		evaluatePolicyUseCase = mock(EvaluatePolicyUseCase.class);
		when(policyEvaluatorProvider.getIfAvailable()).thenReturn(evaluatePolicyUseCase);

		controller = new PolicyController(managePolicyUseCase, currentActorProvider, policyEvaluatorProvider);
	}

	@Test
	@DisplayName("POST /api/v1/policies creates and returns policy")
	void createPolicyReturnsCreated() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		Policy created = new Policy(
				PolicyId.random(),
				tenantId,
				"EhrAccessPolicy",
				"Description",
				PolicyEffect.PERMIT,
				10,
				true,
				new PolicyTarget(Set.of("read"), Set.of("patient_record"), Set.of("DOCTOR")),
				List.of(),
				Instant.now(),
				Instant.now()
		);

		when(managePolicyUseCase.createPolicy(any(CreatePolicyCommand.class))).thenReturn(created);

		CreatePolicyRequest request = new CreatePolicyRequest(
				tenantUuid,
				"EhrAccessPolicy",
				"Description",
				PolicyEffect.PERMIT,
				10,
				true,
				new PolicyTargetDto(Set.of("read"), Set.of("patient_record"), Set.of("DOCTOR")),
				List.of()
		);

		ResponseEntity<PolicyResponse> response = controller.createPolicy(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().name()).isEqualTo("EhrAccessPolicy");
		assertThat(response.getBody().effect()).isEqualTo("PERMIT");
		assertThat(response.getHeaders().getLocation()).isNotNull();
	}

	@Test
	@DisplayName("GET /api/v1/policies/{id} returns matching policy")
	void getPolicyByIdReturnsOk() {
		PolicyId id = PolicyId.random();
		Policy policy = new Policy(
				id,
				null,
				"GlobalPolicy",
				"Desc",
				PolicyEffect.PERMIT,
				1,
				true,
				PolicyTarget.all(),
				List.of(),
				Instant.now(),
				Instant.now()
		);
		when(managePolicyUseCase.getPolicy(id)).thenReturn(Optional.of(policy));

		ResponseEntity<PolicyResponse> response = controller.getPolicyById(id.value());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().name()).isEqualTo("GlobalPolicy");
	}

	@Test
	@DisplayName("GET /api/v1/policies returns list of policies")
	void getPoliciesReturnsList() {
		UUID tenantUuid = UUID.randomUUID();
		Policy policy = new Policy(
				PolicyId.random(),
				new TenantId(tenantUuid),
				"TenantPolicy",
				"Desc",
				PolicyEffect.PERMIT,
				1,
				true,
				PolicyTarget.all(),
				List.of(),
				Instant.now(),
				Instant.now()
		);
		when(managePolicyUseCase.listPolicies(any())).thenReturn(List.of(policy));

		ResponseEntity<List<PolicyResponse>> response = controller.getPolicies(null, tenantUuid);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).hasSize(1);
		assertThat(response.getBody().getFirst().name()).isEqualTo("TenantPolicy");
	}

	@Test
	@DisplayName("PUT /api/v1/policies/{id} updates and returns policy")
	void updatePolicyReturnsOk() {
		PolicyId id = PolicyId.random();
		Policy updated = new Policy(
				id,
				null,
				"UpdatedName",
				"UpdatedDesc",
				PolicyEffect.DENY,
				20,
				false,
				PolicyTarget.all(),
				List.of(),
				Instant.now(),
				Instant.now()
		);
		when(managePolicyUseCase.updatePolicy(any(UpdatePolicyCommand.class))).thenReturn(updated);

		UpdatePolicyRequest request = new UpdatePolicyRequest(
				"UpdatedName",
				"UpdatedDesc",
				PolicyEffect.DENY,
				20,
				false,
				new PolicyTargetDto(Set.of("*"), Set.of("*"), Set.of()),
				List.of()
		);

		ResponseEntity<PolicyResponse> response = controller.updatePolicy(id.value(), null, null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().name()).isEqualTo("UpdatedName");
		assertThat(response.getBody().effect()).isEqualTo("DENY");
		assertThat(response.getBody().enabled()).isFalse();
	}

	@Test
	@DisplayName("DELETE /api/v1/policies/{id} returns no content")
	void deletePolicyReturnsNoContent() {
		PolicyId id = PolicyId.random();
		ResponseEntity<Void> response = controller.deletePolicy(id.value());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(managePolicyUseCase).deletePolicy(id);
	}

	@Test
	@DisplayName("POST /api/v1/policies/evaluate evaluates and returns decision")
	void evaluatePolicyReturnsDecision() {
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(UUID.randomUUID());
		when(actor.tenantId()).thenReturn(UUID.randomUUID());
		when(actor.email()).thenReturn("doctor@clinic.com");
		when(actor.roles()).thenReturn(Set.of("DOCTOR"));
		when(actor.groups()).thenReturn(Set.of());
		when(actor.permissions()).thenReturn(Set.of());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		when(evaluatePolicyUseCase.evaluate(any())).thenReturn(PolicyDecision.permit("Granted by rule"));

		HttpServletRequest servletRequest = mock(HttpServletRequest.class);
		when(servletRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
		when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

		PolicyEvaluationRequestDto request = new PolicyEvaluationRequestDto(
				"read",
				"patient_record",
				"rec-123",
				null,
				null,
				Map.of("department", "cardiology"),
				Map.of()
		);

		ResponseEntity<PolicyEvaluationResponseDto> response = controller.evaluatePolicy(servletRequest, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().permitted()).isTrue();
		assertThat(response.getBody().reason()).isEqualTo("Granted by rule");
	}
}
