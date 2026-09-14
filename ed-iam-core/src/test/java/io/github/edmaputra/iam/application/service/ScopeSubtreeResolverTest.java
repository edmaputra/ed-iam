package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test verifying pure scope tree and subtree resolution algorithms.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class ScopeSubtreeResolverTest {

	private ScopeNodeRepository scopeNodeRepository;
	private ScopeSubtreeResolver resolver;

	@BeforeEach
	void setUp() {
		scopeNodeRepository = Mockito.mock(ScopeNodeRepository.class);
		resolver = new ScopeSubtreeResolver(scopeNodeRepository);
	}

	@Test
	@DisplayName("Should resolve all descendant ScopeNodeIds from assigned nodes")
	void shouldResolveDescendantScopeNodeIds() {
		TenantId tenantId = TenantId.generate();

		ScopeNode root = ScopeNode.createRoot(tenantId, "MAIN", "Main Facility");
		ScopeNode surgeryDiv = ScopeNode.createChild(tenantId, root, "SURGERY", "Surgery Division");
		ScopeNode genSurgery = ScopeNode.createChild(tenantId, surgeryDiv, "GEN_SURGERY", "General Surgery Dept");
		ScopeNode ortho = ScopeNode.createChild(tenantId, surgeryDiv, "ORTHO", "Orthopedics Dept");

		when(scopeNodeRepository.findById(surgeryDiv.getId())).thenReturn(Optional.of(surgeryDiv));
		when(scopeNodeRepository.findDescendantsByPathPrefix(surgeryDiv.getPath()))
				.thenReturn(List.of(genSurgery, ortho));

		Set<ScopeNodeId> accessible = resolver.resolveAccessibleScopeNodeIds(tenantId, List.of(surgeryDiv.getId()));

		assertThat(accessible).containsExactlyInAnyOrder(
				surgeryDiv.getId(),
				genSurgery.getId(),
				ortho.getId());
	}

	@Test
	@DisplayName("Should correctly evaluate scope accessibility")
	void shouldCheckScopeAccessibility() {
		TenantId tenantId = TenantId.generate();

		ScopeNode root = ScopeNode.createRoot(tenantId, "MAIN", "Main Facility");
		ScopeNode surgeryDiv = ScopeNode.createChild(tenantId, root, "SURGERY", "Surgery Division");
		ScopeNode genSurgery = ScopeNode.createChild(tenantId, surgeryDiv, "GEN_SURGERY", "General Surgery Dept");

		ScopeNode pediatricsDiv = ScopeNode.createChild(tenantId, root, "PEDIATRICS", "Pediatrics Division");

		when(scopeNodeRepository.findById(surgeryDiv.getId())).thenReturn(Optional.of(surgeryDiv));
		when(scopeNodeRepository.findDescendantsByPathPrefix(surgeryDiv.getPath())).thenReturn(List.of(genSurgery));
		when(scopeNodeRepository.findById(genSurgery.getId())).thenReturn(Optional.of(genSurgery));
		when(scopeNodeRepository.findById(pediatricsDiv.getId())).thenReturn(Optional.of(pediatricsDiv));

		// Doctor assigned to SURGERY should be able to access GEN_SURGERY
		boolean canAccessGenSurgery = resolver.isScopeAccessible(tenantId, List.of(surgeryDiv.getId()), genSurgery.getId());
		assertThat(canAccessGenSurgery).isTrue();

		// Doctor assigned to SURGERY should NOT be able to access PEDIATRICS
		boolean canAccessPediatrics = resolver.isScopeAccessible(tenantId, List.of(surgeryDiv.getId()), pediatricsDiv.getId());
		assertThat(canAccessPediatrics).isFalse();
	}

	@Test
	@DisplayName("Should evaluate path accessibility via static prefix matching")
	void shouldCheckPathPrefixMatching() {
		String surgeryPath = "/tenant-1/hospital-1/surgery/";
		String genSurgeryPath = "/tenant-1/hospital-1/surgery/gen-surgery/";
		String pediatricsPath = "/tenant-1/hospital-1/pediatrics/";

		Set<String> assignedPaths = Set.of(surgeryPath);

		assertThat(ScopeSubtreeResolver.isPathAccessible(assignedPaths, genSurgeryPath)).isTrue();
		assertThat(ScopeSubtreeResolver.isPathAccessible(assignedPaths, surgeryPath)).isTrue();
		assertThat(ScopeSubtreeResolver.isPathAccessible(assignedPaths, pediatricsPath)).isFalse();
		assertThat(ScopeSubtreeResolver.isPathAccessible(Set.of(), genSurgeryPath)).isFalse();
		assertThat(ScopeSubtreeResolver.isPathAccessible(assignedPaths, null)).isFalse();
	}
}
