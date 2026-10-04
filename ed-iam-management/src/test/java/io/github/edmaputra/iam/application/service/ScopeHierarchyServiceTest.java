package io.github.edmaputra.iam.application.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ScopeHierarchyService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class ScopeHierarchyServiceTest {

	private ScopeNodeRepository scopeNodeRepository;
	private EventPublisherPort eventPublisher;
	private ScopeHierarchyService service;

	@BeforeEach
	void setUp() {
		scopeNodeRepository = mock(ScopeNodeRepository.class);
		eventPublisher = mock(EventPublisherPort.class);
		service = new ScopeHierarchyService(scopeNodeRepository, eventPublisher);
	}

	@Test
	@DisplayName("Should create root scope node when valid command provided")
	void shouldCreateRootScopeNode() {
		TenantId tenantId = TenantId.generate();
		when(scopeNodeRepository.existsByTenantIdAndCode(tenantId, "ROOT")).thenReturn(false);
		when(scopeNodeRepository.save(any(ScopeNode.class))).thenAnswer(i -> i.getArgument(0));

		CreateScopeNodeCommand command = new CreateScopeNodeCommand(tenantId, null, "ROOT", "Root Node");
		ScopeNode created = service.createScopeNode(command, OperationContext.system());

		assertThat(created.getCode()).isEqualTo("ROOT");
		assertThat(created.getName()).isEqualTo("Root Node");
		verify(eventPublisher).publish(any());
	}

	@Test
	@DisplayName("Should prevent non-superadmin actor from creating scope node in a different tenant")
	void shouldRejectCreatingScopeNodeForDifferentTenantWhenNotSuperAdmin() {
		TenantId actorTenantId = TenantId.generate();
		TenantId targetTenantId = TenantId.generate();

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		ScopeHierarchyService securedService = new ScopeHierarchyService(scopeNodeRepository, eventPublisher, actorProvider);

		CreateScopeNodeCommand command = new CreateScopeNodeCommand(targetTenantId, null, "ROOT", "Root Node");

		assertThatThrownBy(() -> securedService.createScopeNode(command, OperationContext.system()))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied: operation not permitted for a different tenant.");
	}

	@Test
	@DisplayName("Should allow superadmin actor to create scope node in any tenant")
	void shouldAllowCreatingScopeNodeForDifferentTenantWhenSuperAdmin() {
		TenantId targetTenantId = TenantId.generate();

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(true);
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		ScopeHierarchyService securedService = new ScopeHierarchyService(scopeNodeRepository, eventPublisher, actorProvider);

		when(scopeNodeRepository.existsByTenantIdAndCode(targetTenantId, "ADMIN_NODE")).thenReturn(false);
		when(scopeNodeRepository.save(any(ScopeNode.class))).thenAnswer(i -> i.getArgument(0));

		CreateScopeNodeCommand command = new CreateScopeNodeCommand(targetTenantId, null, "ADMIN_NODE", "Admin Node");
		ScopeNode created = securedService.createScopeNode(command, OperationContext.system());

		assertThat(created.getCode()).isEqualTo("ADMIN_NODE");
	}

	@Test
	@DisplayName("Should prevent non-superadmin actor from accessing scope node belonging to a different tenant")
	void shouldRejectGettingScopeNodeForDifferentTenantWhenNotSuperAdmin() {
		TenantId actorTenantId = TenantId.generate();
		TenantId otherTenantId = TenantId.generate();
		ScopeNodeId nodeId = ScopeNodeId.generate();

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		ScopeHierarchyService securedService = new ScopeHierarchyService(scopeNodeRepository, eventPublisher, actorProvider);

		assertThatThrownBy(() -> securedService.getById(otherTenantId, nodeId))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied: operation not permitted for a different tenant.");
	}
}
