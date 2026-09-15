package io.github.edmaputra.iam.it;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.in.UpdateScopeNodeCommand;
import io.github.edmaputra.iam.application.service.ScopeSubtreeResolver;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test covering ScopeSubtreeResolver operations and scope node metadata management.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class ScopeSubtreeAndMetadataIT extends AbstractIntegrationTest {

	@Autowired
	private ManageScopeUseCase manageScopeUseCase;

	@Autowired
	private ScopeSubtreeResolver scopeSubtreeResolver;

	@Test
	@DisplayName("Should resolve accessible scope IDs with and without descendant inheritance")
	void shouldResolveAccessibleScopeNodeIds() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		// Root -> Child -> Grandchild
		ScopeNode hospital = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "HOSP", "Main Hospital"),
				context);
		ScopeNode pediatric = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, hospital.getId(), "PED", "Pediatric"),
				context);
		ScopeNode icu = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, pediatric.getId(), "ICU", "Pediatric ICU"),
				context);

		// With inheritChildren = true on Pediatric: includes Pediatric + ICU
		Set<UUID> withInheritance = scopeSubtreeResolver.resolveAccessibleScopeNodeIds(pediatric.getId(), true);
		assertThat(withInheritance).containsExactlyInAnyOrder(pediatric.getId().value(), icu.getId().value());

		// With inheritChildren = false on Pediatric: includes ONLY Pediatric
		Set<UUID> withoutInheritance = scopeSubtreeResolver.resolveAccessibleScopeNodeIds(pediatric.getId(), false);
		assertThat(withoutInheritance).containsExactly(pediatric.getId().value());

		// Batch resolution across assigned nodes
		Set<ScopeNodeId> batchResolved = scopeSubtreeResolver.resolveAccessibleScopeNodeIds(
				tenantId, List.of(pediatric.getId()));
		assertThat(batchResolved).containsExactlyInAnyOrder(pediatric.getId(), icu.getId());

		// Check scope accessibility predicate
		assertThat(scopeSubtreeResolver.isScopeAccessible(tenantId, List.of(pediatric.getId()), icu.getId())).isTrue();
		assertThat(scopeSubtreeResolver.isScopeAccessible(tenantId, List.of(pediatric.getId()), hospital.getId())).isFalse();

		// Path accessibility
		Set<String> prefixes = Set.of(pediatric.getPath());
		assertThat(ScopeSubtreeResolver.isPathAccessible(prefixes, icu.getPath())).isTrue();
		assertThat(ScopeSubtreeResolver.isPathAccessible(prefixes, hospital.getPath())).isFalse();
	}

	@Test
	@DisplayName("Should update scope node metadata (code and name) and reject duplicate code")
	void shouldUpdateScopeMetadata() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		ScopeNode root = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "ORIG_CODE", "Original Name"),
				context);
		manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "OTHER_CODE", "Other Name"),
				context);

		// Update name and code
		ScopeNode updated = manageScopeUseCase.updateMetadata(
				new UpdateScopeNodeCommand(tenantId, root.getId(), "NEW_CODE", "New Updated Name"),
				context);

		assertThat(updated.getCode()).isEqualTo("NEW_CODE");
		assertThat(updated.getName()).isEqualTo("New Updated Name");

		// Reject renaming to an already existing code in the same tenant
		assertThatThrownBy(() -> manageScopeUseCase.updateMetadata(
				new UpdateScopeNodeCommand(tenantId, root.getId(), "OTHER_CODE", "Duplicate Code Attempt"),
				context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("already exists for this tenant");
	}

	@Test
	@DisplayName("Should retrieve flat list of all scope nodes in a tenant")
	void shouldGetFlatScopeList() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		manageScopeUseCase.createScopeNode(CreateScopeNodeCommand.root(tenantId, "NODE_1", "Node 1"), context);
		manageScopeUseCase.createScopeNode(CreateScopeNodeCommand.root(tenantId, "NODE_2", "Node 2"), context);

		List<ScopeNode> flatList = manageScopeUseCase.getFlatScopeList(tenantId);
		assertThat(flatList).hasSize(2);
		assertThat(flatList.stream().map(ScopeNode::getCode)).containsExactlyInAnyOrder("NODE_1", "NODE_2");
	}

	@Test
	@DisplayName("Should handle empty, null, and non-existent boundaries for scope resolution")
	void shouldHandleScopeResolutionBoundaryCases() {
		TenantId tenantId = TenantId.generate();

		// Null and empty assigned lists
		assertThat(scopeSubtreeResolver.resolveAccessibleScopeNodeIds(tenantId, null)).isEmpty();
		assertThat(scopeSubtreeResolver.resolveAccessibleScopeNodeIds(tenantId, List.of())).isEmpty();

		// List containing null element
		List<ScopeNodeId> withNull = new java.util.ArrayList<>();
		withNull.add(null);
		assertThat(scopeSubtreeResolver.resolveAccessibleScopeNodeIds(tenantId, withNull)).isEmpty();

		// Target scope node is null
		assertThat(scopeSubtreeResolver.isScopeAccessible(tenantId, List.of(), null)).isFalse();

		// Non-existent node ID
		ScopeNodeId nonExistent = ScopeNodeId.generate();
		assertThat(scopeSubtreeResolver.resolveAccessibleScopeNodeIds(nonExistent, true)).isEmpty();
	}
}
