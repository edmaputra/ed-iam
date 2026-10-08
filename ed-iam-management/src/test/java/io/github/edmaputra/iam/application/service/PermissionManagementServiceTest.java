package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.in.PermissionCommands.CreatePermissionCommand;
import io.github.edmaputra.iam.application.port.in.PermissionCommands.UpdatePermissionCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.PermissionNotFoundException;
import io.github.edmaputra.iam.domain.model.Permission;
import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.repository.PermissionRepository;
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
 * Unit tests for {@link PermissionManagementService}.
 *
 * @author edmaputra
 * @since 0.10.0
 */
class PermissionManagementServiceTest {

	private PermissionRepository repository;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;
	private PermissionManagementService service;

	@BeforeEach
	void setUp() {
		repository = mock(PermissionRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);
		service = new PermissionManagementService(repository, currentActorProvider, eventPublisher);
	}

	@Test
	@DisplayName("Should create custom tenant permission successfully and publish event")
	void shouldCreateCustomPermission() {
		TenantId tenantId = TenantId.generate();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(tenantId.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.userId()).thenReturn(UUID.randomUUID());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		when(repository.existsByTenantIdAndCode(tenantId, "ehr:patient:read")).thenReturn(false);
		when(repository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CreatePermissionCommand command = new CreatePermissionCommand(
				tenantId,
				"ehr:patient:read",
				"Read Patient Records",
				"Allows reading patients",
				"EHR");

		Permission created = service.createPermission(command);

		assertThat(created).isNotNull();
		assertThat(created.code()).isEqualTo("ehr:patient:read");
		assertThat(created.systemPermission()).isFalse();

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.PERMISSION_CREATED);
	}

	@Test
	@DisplayName("Should prevent non-superadmin from creating global system permission")
	void shouldPreventNonSuperAdminFromCreatingSystemPermission() {
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		CreatePermissionCommand command = new CreatePermissionCommand(
				null,
				"sys:perm",
				"System Permission",
				null,
				"SYS");

		assertThatThrownBy(() -> service.createPermission(command))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Only platform superadmins");
	}

	@Test
	@DisplayName("Should allow superadmin to create global system permission")
	void shouldAllowSuperAdminToCreateSystemPermission() {
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(true);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		when(repository.existsSystemPermissionByCode("iam:super:action")).thenReturn(false);
		when(repository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CreatePermissionCommand command = new CreatePermissionCommand(
				null,
				"iam:super:action",
				"Super Action",
				null,
				"IAM");

		Permission created = service.createPermission(command);
		assertThat(created.systemPermission()).isTrue();
		assertThat(created.tenantId()).isNull();
	}

	@Test
	@DisplayName("Should throw exception if permission code already exists")
	void shouldThrowIfCodeAlreadyExists() {
		TenantId tenantId = TenantId.generate();
		when(currentActorProvider.currentActor()).thenReturn(Optional.empty());
		when(repository.existsByTenantIdAndCode(tenantId, "ehr:dup")).thenReturn(true);

		CreatePermissionCommand command = new CreatePermissionCommand(
				tenantId,
				"ehr:dup",
				"Duplicate",
				null,
				"EHR");

		assertThatThrownBy(() -> service.createPermission(command))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("already exists for tenant");
	}

	@Test
	@DisplayName("Should retrieve permission by ID")
	void shouldGetPermissionById() {
		Permission perm = Permission.createSystem("code1", "Name 1", "Desc", "CAT");
		when(repository.findById(perm.id())).thenReturn(Optional.of(perm));

		Permission result = service.getPermissionById(perm.id());
		assertThat(result).isEqualTo(perm);
	}

	@Test
	@DisplayName("Should throw PermissionNotFoundException when ID does not exist")
	void shouldThrowWhenIdNotFound() {
		PermissionId id = PermissionId.generate();
		when(repository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPermissionById(id))
				.isInstanceOf(PermissionNotFoundException.class);
	}

	@Test
	@DisplayName("Should retrieve permission by code")
	void shouldGetPermissionByCode() {
		TenantId tenantId = TenantId.generate();
		Permission perm = Permission.createCustom(tenantId, "my:code", "My Name", null, "CAT");
		when(repository.findByTenantIdOrGlobalByCode(tenantId, "my:code")).thenReturn(Optional.of(perm));

		Permission result = service.getPermissionByCode(tenantId, "my:code");
		assertThat(result).isEqualTo(perm);
	}

	@Test
	@DisplayName("Should list permissions with category filter")
	void shouldListPermissionsWithCategory() {
		TenantId tenantId = TenantId.generate();
		Permission p1 = Permission.createCustom(tenantId, "code:1", "Name 1", null, "CAT_A");
		when(repository.findAllByTenantIdOrGlobalAndCategory(tenantId, "CAT_A")).thenReturn(List.of(p1));

		List<Permission> results = service.getPermissions(tenantId, "CAT_A");
		assertThat(results).containsExactly(p1);
	}

	@Test
	@DisplayName("Should list all global permissions when tenantId is null")
	void shouldListGlobalPermissions() {
		Permission p1 = Permission.createSystem("sys:1", "System 1", null, "CAT");
		when(repository.findAllGlobal()).thenReturn(List.of(p1));

		List<Permission> results = service.getPermissions(null, null);
		assertThat(results).containsExactly(p1);
	}

	@Test
	@DisplayName("Should update custom permission successfully and publish event")
	void shouldUpdateCustomPermission() {
		TenantId tenantId = TenantId.generate();
		Permission custom = Permission.createCustom(tenantId, "edit:me", "Old Name", "Old Desc", "OLD_CAT");
		when(repository.findById(custom.id())).thenReturn(Optional.of(custom));
		when(repository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UpdatePermissionCommand command = new UpdatePermissionCommand(
				custom.id(),
				"New Name",
				"New Desc",
				"NEW_CAT");

		Permission updated = service.updatePermission(command);
		assertThat(updated.name()).isEqualTo("New Name");
		assertThat(updated.description()).isEqualTo("New Desc");
		assertThat(updated.category()).isEqualTo("NEW_CAT");

		verify(eventPublisher).publish(any(IamEvent.class));
	}

	@Test
	@DisplayName("Should reject updating system permission")
	void shouldRejectUpdatingSystemPermission() {
		Permission system = Permission.createSystem("sys:perm", "Sys Name", null, "SYS");
		when(repository.findById(system.id())).thenReturn(Optional.of(system));

		UpdatePermissionCommand command = new UpdatePermissionCommand(
				system.id(),
				"New Name",
				null,
				"SYS");

		assertThatThrownBy(() -> service.updatePermission(command))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("System permission cannot be modified");
	}

	@Test
	@DisplayName("Should delete custom permission successfully and publish event")
	void shouldDeleteCustomPermission() {
		TenantId tenantId = TenantId.generate();
		Permission custom = Permission.createCustom(tenantId, "del:me", "Del Name", null, "CAT");
		when(repository.findById(custom.id())).thenReturn(Optional.of(custom));

		service.deletePermission(custom.id());

		verify(repository).delete(custom.id());
		verify(eventPublisher).publish(any(IamEvent.class));
	}

	@Test
	@DisplayName("Should reject deleting system permission")
	void shouldRejectDeletingSystemPermission() {
		Permission system = Permission.createSystem("sys:perm", "Sys Name", null, "SYS");
		when(repository.findById(system.id())).thenReturn(Optional.of(system));

		assertThatThrownBy(() -> service.deletePermission(system.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("System permission cannot be deleted");
	}
}
