package io.github.edmaputra.iam.domain.repository;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Outbound domain repository contract (SPI) for ABAC policy persistence and retrieval.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public interface PolicyRepository {

	/**
	 * Saves or updates a policy entity.
	 *
	 * @param policy the policy to save
	 * @return the saved policy
	 */
	Policy save(Policy policy);

	/**
	 * Finds a policy by its unique identifier.
	 *
	 * @param id the policy ID
	 * @return optional policy
	 */
	Optional<Policy> findById(PolicyId id);

	/**
	 * Finds a policy by name within a tenant context (or global context if tenantId is null).
	 *
	 * @param name the policy name
	 * @param tenantId the owning tenant ID, or null for global policies
	 * @return optional policy
	 */
	Optional<Policy> findByNameAndTenantId(String name, TenantId tenantId);

	/**
	 * Retrieves all policies belonging to a tenant (including global system policies if applicable).
	 *
	 * @param tenantId the tenant ID
	 * @return list of policies
	 */
	List<Policy> findByTenantId(TenantId tenantId);

	/**
	 * Retrieves all active/enabled policies applicable to the given tenant (including global active policies).
	 *
	 * @param tenantId the tenant ID
	 * @return list of active policies
	 */
	List<Policy> findAllActive(TenantId tenantId);

	/**
	 * Checks whether a policy with the given name exists in the specified tenant context.
	 *
	 * @param name the policy name
	 * @param tenantId the tenant ID, or null for global policies
	 * @return true if exists, false otherwise
	 */
	boolean existsByNameAndTenantId(String name, TenantId tenantId);

	/**
	 * Deletes a policy by its unique identifier.
	 *
	 * @param id the policy ID
	 */
	void deleteById(PolicyId id);
}
