package io.github.edmaputra.iam.application.port.in;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Driving use case interface for administrative management of ABAC policies.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public interface ManagePolicyUseCase {

	/**
	 * Provisions a new ABAC policy.
	 *
	 * @param command creation command
	 * @return created policy
	 */
	Policy createPolicy(PolicyCommands.CreatePolicyCommand command);

	/**
	 * Modifies an existing ABAC policy.
	 *
	 * @param command update command
	 * @return updated policy
	 */
	Policy updatePolicy(PolicyCommands.UpdatePolicyCommand command);

	/**
	 * Retrieves a policy by its ID.
	 *
	 * @param id policy ID
	 * @return optional policy
	 */
	Optional<Policy> getPolicy(PolicyId id);

	/**
	 * Lists all policies belonging to the given tenant (including platform policies).
	 *
	 * @param tenantId target tenant ID
	 * @return list of policies
	 */
	List<Policy> listPolicies(TenantId tenantId);

	/**
	 * Deletes a policy by its ID.
	 *
	 * @param id policy ID
	 */
	void deletePolicy(PolicyId id);
}
