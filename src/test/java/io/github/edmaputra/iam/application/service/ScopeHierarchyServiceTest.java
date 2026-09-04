package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.model.ScopeTreeNode;
import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.DeleteScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.MoveScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.UpdateScopeNodeCommand;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying scope hierarchy management operations (create, move, update, delete).
 */
class ScopeHierarchyServiceTest {

	private ScopeNodeRepository scopeNodeRepository;
	private ApplicationEventPublisher eventPublisher;
	private ScopeHierarchyService service;

	private final TenantId tenantId = TenantId.generate();
	private final OperationContext context = OperationContext.of("admin@hospital.org", "trace-001");

	@BeforeEach
	void setUp() {
		scopeNodeRepository = Mockito.mock(ScopeNodeRepository.class);
		eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
		service = new ScopeHierarchyService(scopeNodeRepository, eventPublisher);
	}

	@Test
	@DisplayName("Should create root scope node and publish domain event")
	void shouldCreateRootNode() {
		when(scopeNodeRepository.existsByTenantIdAndCode(tenantId, "MAIN")).thenReturn(false);
		when(scopeNodeRepository.save(any(ScopeNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ScopeNode root = service.createScopeNode(CreateScopeNodeCommand.root(tenantId, "MAIN", "Main Facility"), context);

		assertThat(root).isNotNull();
		assertThat(root.getCode()).isEqualTo("MAIN");
		assertThat(root.isRoot()).isTrue();

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		IamEvent captured = eventCaptor.getValue();
		assertThat(captured.eventType()).isEqualTo(IamEventTypes.SCOPE_NODE_CREATED);
		assertThat(captured.actor()).isEqualTo("admin@hospital.org");
	}

	@Test
	@DisplayName("Should reject creating duplicate code within the same tenant")
	void shouldRejectDuplicateCode() {
		when(scopeNodeRepository.existsByTenantIdAndCode(tenantId, "MAIN")).thenReturn(true);

		assertThatThrownBy(() -> service.createScopeNode(CreateScopeNodeCommand.root(tenantId, "MAIN", "Main Facility"), context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("already exists");

		verify(scopeNodeRepository, never()).save(any());
	}

	@Test
	@DisplayName("Should create child node with cumulative path")
	void shouldCreateChildNode() {
		ScopeNode root = ScopeNode.createRoot(tenantId, "MAIN", "Main Facility");

		when(scopeNodeRepository.findById(root.getId())).thenReturn(Optional.of(root));
		when(scopeNodeRepository.existsByTenantIdAndCode(tenantId, "SURGERY")).thenReturn(false);
		when(scopeNodeRepository.save(any(ScopeNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ScopeNode child = service.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, root.getId(), "SURGERY", "Surgery Dept"),
				context);

		assertThat(child.getParentId()).isEqualTo(root.getId());
		assertThat(child.getPath()).startsWith(root.getPath());
		verify(eventPublisher).publishEvent(any(IamEvent.class));
	}

	@Test
	@DisplayName("Should move node and cascade path updates to descendants")
	void shouldMoveNodeAndCascadePaths() {
		ScopeNode root1 = ScopeNode.createRoot(tenantId, "FACILITY_1", "Facility 1");
		ScopeNode root2 = ScopeNode.createRoot(tenantId, "FACILITY_2", "Facility 2");
		ScopeNode dept = ScopeNode.createChild(tenantId, root1, "DEPT_A", "Dept A");

		String oldPath = dept.getPath();

		when(scopeNodeRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
		when(scopeNodeRepository.findById(root2.getId())).thenReturn(Optional.of(root2));
		when(scopeNodeRepository.save(any(ScopeNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ScopeNode moved = service.moveNode(new MoveScopeNodeCommand(tenantId, dept.getId(), root2.getId()), context);

		assertThat(moved.getParentId()).isEqualTo(root2.getId());
		String expectedNewPath = root2.getPath() + dept.getId().value() + "/";
		assertThat(moved.getPath()).isEqualTo(expectedNewPath);

		verify(scopeNodeRepository).updatePathPrefix(eq(oldPath), eq(expectedNewPath));

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.SCOPE_NODE_MOVED);
	}

	@Test
	@DisplayName("Should prevent cycle when moving a node under its own descendant")
	void shouldPreventCycleOnMove() {
		ScopeNode root = ScopeNode.createRoot(tenantId, "HOSPITAL", "Hospital");
		ScopeNode div = ScopeNode.createChild(tenantId, root, "SURGERY_DIV", "Surgery Div");
		ScopeNode dept = ScopeNode.createChild(tenantId, div, "ORTHO_DEPT", "Ortho Dept");

		when(scopeNodeRepository.findById(div.getId())).thenReturn(Optional.of(div));
		when(scopeNodeRepository.findById(dept.getId())).thenReturn(Optional.of(dept));

		// Attempting to move div under dept (its own child)
		assertThatThrownBy(() -> service.moveNode(new MoveScopeNodeCommand(tenantId, div.getId(), dept.getId()), context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cycle detected");

		verify(scopeNodeRepository, never()).updatePathPrefix(any(), any());
	}

	@Test
	@DisplayName("Should update scope node metadata")
	void shouldUpdateMetadata() {
		ScopeNode root = ScopeNode.createRoot(tenantId, "MAIN", "Main Facility");

		when(scopeNodeRepository.findById(root.getId())).thenReturn(Optional.of(root));
		when(scopeNodeRepository.save(any(ScopeNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ScopeNode updated = service.updateMetadata(
				new UpdateScopeNodeCommand(tenantId, root.getId(), "MAIN_UPDATED", "Main Facility Updated"),
				context);

		assertThat(updated.getCode()).isEqualTo("MAIN_UPDATED");
		assertThat(updated.getName()).isEqualTo("Main Facility Updated");
		verify(eventPublisher).publishEvent(any(IamEvent.class));
	}

	@Test
	@DisplayName("Should prevent deleting scope node with children")
	void shouldPreventDeletingNodeWithChildren() {
		ScopeNode root = ScopeNode.createRoot(tenantId, "HOSPITAL", "Hospital");

		when(scopeNodeRepository.findById(root.getId())).thenReturn(Optional.of(root));
		when(scopeNodeRepository.existsByParentId(root.getId())).thenReturn(true);

		assertThatThrownBy(() -> service.deleteNode(new DeleteScopeNodeCommand(tenantId, root.getId()), context))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("has child nodes");

		verify(scopeNodeRepository, never()).delete(any());
	}

	@Test
	@DisplayName("Should retrieve scope tree and flat list")
	void shouldRetrieveTreeAndFlatList() {
		ScopeNode root = ScopeNode.createRoot(tenantId, "HOSPITAL", "Hospital");
		ScopeNode child = ScopeNode.createChild(tenantId, root, "CLINIC", "Clinic");

		when(scopeNodeRepository.findAllByTenantId(tenantId)).thenReturn(List.of(root, child));

		List<ScopeTreeNode> tree = service.getScopeTree(tenantId);
		assertThat(tree).hasSize(1);
		assertThat(tree.getFirst().children()).hasSize(1);

		List<ScopeNode> flat = service.getFlatScopeList(tenantId);
		assertThat(flat).hasSize(2);
	}
}
