package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.domain.policy.DecisionEffect;
import io.github.edmaputra.iam.domain.policy.DevicePosture;
import io.github.edmaputra.iam.domain.policy.EnvironmentAttributes;
import io.github.edmaputra.iam.domain.policy.NetworkCidrConstraint;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyCombiningAlgorithm;
import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyEvaluationContext;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.policy.ResourceAttributes;
import io.github.edmaputra.iam.domain.policy.SubjectAttributes;
import io.github.edmaputra.iam.domain.repository.PolicyRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicPolicyEvaluationService Unit Tests")
class DynamicPolicyEvaluationServiceTest {

	@Mock
	private PolicyRepository policyRepository;

	private final TenantId tenantId = TenantId.generate();
	private final UUID userId = UUID.randomUUID();

	private DynamicPolicyEvaluationService evaluationService;

	@BeforeEach
	void setUp() {
		evaluationService = new DynamicPolicyEvaluationService(policyRepository, PolicyCombiningAlgorithm.DENY_OVERRIDES);
	}

	private PolicyEvaluationContext createContext(String action, String resourceType, String clientIp) {
		return new PolicyEvaluationContext(
				new SubjectAttributes(userId, tenantId.value(), "doc@metro.org", Set.of("CLINICIAN"), Set.of(), Set.of(), Map.of()),
				action,
				ResourceAttributes.ofType(resourceType),
				new EnvironmentAttributes(clientIp, Instant.now(), "Mozilla/5.0", DevicePosture.unknown(), Map.of())
		);
	}

	@Test
	@DisplayName("Should permit immediately when subject is platform superadmin")
	void shouldPermitPlatformSuperAdmin() {
		PolicyEvaluationContext context = new PolicyEvaluationContext(
				new SubjectAttributes(userId, tenantId.value(), "super@platform.org", Set.of("ROLE_PLATFORM_SUPER_ADMIN"), Set.of(), Set.of(), Map.of()),
				"delete",
				ResourceAttributes.ofType("critical-system"),
				new EnvironmentAttributes("1.2.3.4", Instant.now(), "curl", DevicePosture.unknown(), Map.of())
		);

		PolicyDecision decision = evaluationService.evaluate(context);
		assertThat(decision.isPermitted()).isTrue();
		assertThat(decision.effect()).isEqualTo(DecisionEffect.PERMIT);
	}

	@Test
	@DisplayName("DENY_OVERRIDES: Should deny when one matching policy explicitly denies")
	void shouldDenyWhenOnePolicyDeniesUnderDenyOverrides() {
		Policy allowAll = new Policy(
				PolicyId.generate(), tenantId, "allow-records", null,
				PolicyEffect.PERMIT, 10, true,
				PolicyTarget.of("read", "patient-record"), List.of(),
				Instant.now(), Instant.now()
		);

		// Deny if coming from untrusted network
		Policy denyUntrustedNetwork = new Policy(
				PolicyId.generate(), tenantId, "block-external-ips", null,
				PolicyEffect.DENY, 20, true,
				PolicyTarget.of("read", "patient-record"),
				List.of(NetworkCidrConstraint.allowOnly(Set.of("203.0.113.0/24"))),
				Instant.now(), Instant.now()
		);

		when(policyRepository.findAllActive(any())).thenReturn(List.of(allowAll, denyUntrustedNetwork));

		// IP matches blocked CIDR
		PolicyEvaluationContext blockedContext = createContext("read", "patient-record", "203.0.113.50");
		PolicyDecision decision = evaluationService.evaluate(blockedContext);

		assertThat(decision.isDenied()).isTrue();
		assertThat(decision.effect()).isEqualTo(DecisionEffect.DENY);
		assertThat(decision.reason()).contains("block-external-ips");

		// IP is clean
		PolicyEvaluationContext safeContext = createContext("read", "patient-record", "192.168.1.100");
		PolicyDecision safeDecision = evaluationService.evaluate(safeContext);

		assertThat(safeDecision.isPermitted()).isTrue();
		assertThat(safeDecision.effect()).isEqualTo(DecisionEffect.PERMIT);
	}

	@Test
	@DisplayName("FIRST_APPLICABLE: First matching policy by priority wins")
	void shouldRespectFirstApplicable() {
		DynamicPolicyEvaluationService firstApplicableService =
				new DynamicPolicyEvaluationService(policyRepository, PolicyCombiningAlgorithm.FIRST_APPLICABLE);

		Policy highPriorityDeny = new Policy(
				PolicyId.generate(), tenantId, "emergency-lockdown", null,
				PolicyEffect.DENY, 100, true,
				PolicyTarget.all(), List.of(),
				Instant.now(), Instant.now()
		);

		Policy lowPriorityPermit = new Policy(
				PolicyId.generate(), tenantId, "normal-permit", null,
				PolicyEffect.PERMIT, 10, true,
				PolicyTarget.all(), List.of(),
				Instant.now(), Instant.now()
		);

		when(policyRepository.findAllActive(any())).thenReturn(List.of(lowPriorityPermit, highPriorityDeny));

		PolicyDecision decision = firstApplicableService.evaluate(createContext("read", "patient-record", "10.0.0.1"));

		assertThat(decision.isDenied()).isTrue();
		assertThat(decision.reason()).contains("emergency-lockdown");
	}

	@Test
	@DisplayName("Should return NOT_APPLICABLE when no policies match")
	void shouldReturnNotApplicableWhenNoPolicyMatches() {
		Policy billingPolicy = new Policy(
				PolicyId.generate(), tenantId, "billing-policy", null,
				PolicyEffect.PERMIT, 10, true,
				PolicyTarget.of("read", "billing-invoice"), List.of(),
				Instant.now(), Instant.now()
		);

		when(policyRepository.findAllActive(any())).thenReturn(List.of(billingPolicy));

		PolicyDecision decision = evaluationService.evaluate(createContext("read", "patient-record", "10.0.0.1"));

		assertThat(decision.effect()).isEqualTo(DecisionEffect.NOT_APPLICABLE);
	}
}
