package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.in.RoleCommands.CreateRoleCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.UpdateRoleCommand;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RoleManagementService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class RoleManagementServiceTest {

	private RoleRepository roleRepository;
	private RoleManagementService service;

	@BeforeEach
	void setUp() {
		roleRepository = mock(RoleRepository.class);
		service = new RoleManagementService(roleRepository);
	}

	@Test
	@DisplayName("Should create role and check duplicate code")
	void shouldCreateRole() {
		TenantId tenantId = TenantId.generate();
		when(roleRepository.existsByTenantIdAndCode(tenantId, "NURSE")).thenReturn(false);
		when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

		Role role = service.createRole(new CreateRoleCommand(tenantId, "NURSE", "Nurse", "Desc", Set.of("READ")));
		assertThat(role.getCode()).isEqualTo("NURSE");

		// Duplicate code
		when(roleRepository.existsByTenantIdAndCode(tenantId, "NURSE")).thenReturn(true);
		assertThatThrownBy(() -> service.createRole(new CreateRoleCommand(tenantId, "NURSE", "Nurse", "Desc", Set.of("READ"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should get role by ID, get by tenant, and update role")
	void shouldGetAndUpdateRole() {
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		Role role = Role.createCustom(tenantId, "NURSE", "Nurse", "Desc", Set.of("READ"));

		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
		when(roleRepository.findAllByTenantIdOrGlobal(tenantId)).thenReturn(List.of(role));
		when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

		assertThat(service.getRoleById(roleId)).isSameAs(role);
		assertThat(service.getRolesByTenant(tenantId)).containsExactly(role);

		Role updated = service.updateRole(new UpdateRoleCommand(roleId, "Senior Nurse", "Senior Desc", Set.of("READ", "WRITE")));
		assertThat(updated.getName()).isEqualTo("Senior Nurse");
	}

	@Test
	@DisplayName("Should throw RoleNotFoundException when role absent")
	void shouldThrowWhenAbsent() {
		RoleId missingId = RoleId.generate();
		when(roleRepository.findById(missingId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getRoleById(missingId)).isInstanceOf(RoleNotFoundException.class);
	}

	@Test
	@DisplayName("Should delete custom role but forbid deleting system role")
	void shouldDeleteRole() {
		RoleId customId = RoleId.generate();
		Role custom = Role.createCustom(TenantId.generate(), "CUSTOM", "Custom", "Desc", Set.of());
		when(roleRepository.findById(customId)).thenReturn(Optional.of(custom));

		service.deleteRole(customId);
		verify(roleRepository).delete(customId);

		RoleId systemId = RoleId.generate();
		Role sys = Role.createSystemRole("SYS_ADMIN", "System Admin", "Desc", Set.of());
		when(roleRepository.findById(systemId)).thenReturn(Optional.of(sys));

		assertThatThrownBy(() -> service.deleteRole(systemId)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("Should prevent non-superadmin actor from creating role in a different tenant")
	void shouldRejectCreatingRoleForDifferentTenantWhenNotSuperAdmin() {
		TenantId actorTenantId = TenantId.generate();
		TenantId otherTenantId = TenantId.generate();

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		RoleManagementService securedService = new RoleManagementService(roleRepository, actorProvider);

		CreateRoleCommand command = new CreateRoleCommand(otherTenantId, "OTHER_ROLE", "Other Role", "Desc", Set.of());

		assertThatThrownBy(() -> securedService.createRole(command))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied: operation not permitted for a different tenant.");
	}

	@Test
	@DisplayName("Should allow superadmin actor to create role in any tenant")
	void shouldAllowCreatingRoleForDifferentTenantWhenSuperAdmin() {
		TenantId targetTenantId = TenantId.generate();

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(true);
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		RoleManagementService securedService = new RoleManagementService(roleRepository, actorProvider);

		when(roleRepository.existsByTenantIdAndCode(targetTenantId, "NEW_ROLE")).thenReturn(false);
		when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

		CreateRoleCommand command = new CreateRoleCommand(targetTenantId, "NEW_ROLE", "New Role", "Desc", Set.of());
		Role role = securedService.createRole(command);

		assertThat(role.getCode()).isEqualTo("NEW_ROLE");
	}

	@Test
	@DisplayName("Should prevent non-superadmin actor from reading role of a different tenant")
	void shouldRejectAccessingRoleFromDifferentTenantWhenNotSuperAdmin() {
		TenantId actorTenantId = TenantId.generate();
		TenantId otherTenantId = TenantId.generate();
		RoleId roleId = RoleId.generate();
		Role role = Role.createCustom(otherTenantId, "OTHER_ROLE", "Other Role", "Desc", Set.of());

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		RoleManagementService securedService = new RoleManagementService(roleRepository, actorProvider);
		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

		assertThatThrownBy(() -> securedService.getRoleById(roleId))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied: operation not permitted for a different tenant.");
	}

	@Test
	@DisplayName("Should allow reading global system role from any tenant")
	void shouldAllowAccessingGlobalSystemRoleFromAnyTenant() {
		TenantId actorTenantId = TenantId.generate();
		RoleId roleId = RoleId.generate();
		Role sysRole = Role.createSystemRole("SYS_ROLE", "System Role", "Desc", Set.of());

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		RoleManagementService securedService = new RoleManagementService(roleRepository, actorProvider);
		when(roleRepository.findById(roleId)).thenReturn(Optional.of(sysRole));

		Role result = securedService.getRoleById(roleId);
		assertThat(result.getCode()).isEqualTo("SYS_ROLE");
	}

	@Test
	@DisplayName("Should publish audit events for role lifecycle")
	void shouldPublishAuditEventsForRoleLifecycle() {
		TenantId tenantId = TenantId.generate();
		EventPublisherPort publisher = mock(EventPublisherPort.class);
		RoleManagementService eventService = new RoleManagementService(roleRepository, null, publisher);

		when(roleRepository.existsByTenantIdAndCode(tenantId, "TEST_ROLE")).thenReturn(false);
		when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

		Role role = eventService.createRole(new CreateRoleCommand(tenantId, "TEST_ROLE", "Test Role", "Desc", Set.of("READ")));
		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(publisher).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.ROLE_CREATED);
		assertThat(captor.getValue().entityId()).isEqualTo(role.getId().value());

		when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
		eventService.updateRole(new UpdateRoleCommand(role.getId(), "Updated Name", "Updated Desc", Set.of("WRITE")));
		verify(publisher, times(2)).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.ROLE_MODIFIED);

		eventService.deleteRole(role.getId());
		verify(publisher, times(3)).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.ROLE_DELETED);
	}
}
