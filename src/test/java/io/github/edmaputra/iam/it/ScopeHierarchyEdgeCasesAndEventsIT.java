package io.github.edmaputra.iam.it;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.DeleteScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.in.MoveScopeNodeCommand;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.ScopeNodeNotFoundException;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test covering scope hierarchy edge cases:
 * cycle detection, deep cascading path recalculations, parent deletion guards, and Spring IamEvent publishing.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@Import(ScopeHierarchyEdgeCasesAndEventsIT.EventCaptureConfig.class)
class ScopeHierarchyEdgeCasesAndEventsIT extends AbstractIntegrationTest {

	@TestConfiguration
	static class EventCaptureConfig {
		final List<IamEvent> events = new CopyOnWriteArrayList<>();

		@EventListener
		void capture(IamEvent event) {
			events.add(event);
		}
	}

	@Autowired
	private ManageScopeUseCase manageScopeUseCase;

	@Autowired
	private EventCaptureConfig eventCaptureConfig;

	@Test
	@DisplayName("Should reject moving a node under one of its own descendants or under itself (cycle detection)")
	void shouldPreventCyclesInScopeHierarchy() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		// Root -> Child -> Grandchild
		ScopeNode root = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "HOSPITAL", "General Hospital"),
				context);
		ScopeNode child = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, root.getId(), "WING_A", "Wing A"),
				context);
		ScopeNode grandchild = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, child.getId(), "ROOM_101", "Room 101"),
				context);

		// Attempt 1: Move Root under its Grandchild
		assertThatThrownBy(() -> manageScopeUseCase.moveNode(
				new MoveScopeNodeCommand(tenantId, root.getId(), grandchild.getId()),
				context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cycle detected");

		// Attempt 2: Move Child under Grandchild
		assertThatThrownBy(() -> manageScopeUseCase.moveNode(
				new MoveScopeNodeCommand(tenantId, child.getId(), grandchild.getId()),
				context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cycle detected");

		// Attempt 3: Move Root under Root itself
		assertThatThrownBy(() -> manageScopeUseCase.moveNode(
				new MoveScopeNodeCommand(tenantId, root.getId(), root.getId()),
				context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cycle detected");
	}

	@Test
	@DisplayName("Should deeply recalculate materialized paths for multi-level descendants upon subtree move")
	void shouldCascadinglyRecalculateDeepDescendantPaths() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		// Hierarchy 1: Old Campus -> Clinic -> Lab -> Freezer
		ScopeNode oldCampus = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "OLD_CAMPUS", "Old Campus"),
				context);
		ScopeNode clinic = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, oldCampus.getId(), "CLINIC", "Clinic Building"),
				context);
		ScopeNode lab = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, clinic.getId(), "LAB", "Biochem Lab"),
				context);
		ScopeNode freezer = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, lab.getId(), "FREEZER", "Cryo Freezer"),
				context);

		// Hierarchy 2: New Campus (New Root)
		ScopeNode newCampus = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "NEW_CAMPUS", "New Campus"),
				context);

		// Move Clinic from Old Campus to New Campus
		ScopeNode movedClinic = manageScopeUseCase.moveNode(
				new MoveScopeNodeCommand(tenantId, clinic.getId(), newCampus.getId()),
				context);

		// Verify Clinic path
		assertThat(movedClinic.getPath()).isEqualTo(newCampus.getPath() + clinic.getId().value() + "/");

		// Verify deep descendants paths were updated in database
		ScopeNode reloadedLab = manageScopeUseCase.getById(tenantId, lab.getId());
		ScopeNode reloadedFreezer = manageScopeUseCase.getById(tenantId, freezer.getId());

		assertThat(reloadedLab.getPath()).isEqualTo(movedClinic.getPath() + lab.getId().value() + "/");
		assertThat(reloadedFreezer.getPath()).isEqualTo(reloadedLab.getPath() + freezer.getId().value() + "/");
	}

	@Test
	@DisplayName("Should prevent deleting a parent scope node with active children and allow deleting leaf node")
	void shouldEnforceChildDeletionGuards() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		ScopeNode building = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "BLDG_X", "Building X"),
				context);
		ScopeNode floor = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, building.getId(), "FL_1", "Floor 1"),
				context);

		// Cannot delete Building while Floor exists
		assertThatThrownBy(() -> manageScopeUseCase.deleteNode(
				new DeleteScopeNodeCommand(tenantId, building.getId()),
				context))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("has child nodes");

		// Deleting leaf node succeeds
		manageScopeUseCase.deleteNode(new DeleteScopeNodeCommand(tenantId, floor.getId()), context);

		assertThatThrownBy(() -> manageScopeUseCase.getById(tenantId, floor.getId()))
				.isInstanceOf(ScopeNodeNotFoundException.class);

		// Now building is a leaf node, can be deleted
		manageScopeUseCase.deleteNode(new DeleteScopeNodeCommand(tenantId, building.getId()), context);

		assertThatThrownBy(() -> manageScopeUseCase.getById(tenantId, building.getId()))
				.isInstanceOf(ScopeNodeNotFoundException.class);
	}

	@Test
	@DisplayName("Should publish Spring IamEvent on ApplicationEventPublisher during lifecycle operations")
	void shouldPublishIamEventsOnScopeOperations() {
		TenantId tenantId = TenantId.generate();
		String actorId = "user-" + UUID.randomUUID();
		String correlationId = "corr-" + UUID.randomUUID();
		OperationContext context = OperationContext.of(actorId, correlationId);

		eventCaptureConfig.events.clear();

		// 1. Create root node
		ScopeNode node = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "EVT_NODE", "Event Node"),
				context);

		// 2. Delete node
		manageScopeUseCase.deleteNode(new DeleteScopeNodeCommand(tenantId, node.getId()), context);

		// Assert published events
		List<IamEvent> events = eventCaptureConfig.events.stream()
				.filter(e -> tenantId.value().equals(e.tenantId()))
				.toList();

		assertThat(events).hasSize(2);

		IamEvent createdEvent = events.get(0);
		assertThat(createdEvent.eventType()).isEqualTo(IamEventTypes.SCOPE_NODE_CREATED);
		assertThat(createdEvent.entityType()).isEqualTo("SCOPE_NODE");
		assertThat(createdEvent.entityId()).isEqualTo(node.getId().value());
		assertThat(createdEvent.actor()).isEqualTo(actorId);
		assertThat(createdEvent.correlationId()).isEqualTo(correlationId);

		IamEvent deletedEvent = events.get(1);
		assertThat(deletedEvent.eventType()).isEqualTo(IamEventTypes.SCOPE_NODE_DELETED);
		assertThat(deletedEvent.entityId()).isEqualTo(node.getId().value());
	}
}
