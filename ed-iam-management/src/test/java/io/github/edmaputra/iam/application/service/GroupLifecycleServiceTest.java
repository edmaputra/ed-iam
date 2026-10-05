package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.in.GroupCommands.CreateGroupCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.UpdateGroupCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link GroupLifecycleService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class GroupLifecycleServiceTest {

	private GroupRepository groupRepository;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;

	private GroupLifecycleService service;

	@BeforeEach
	void setUp() {
		groupRepository = mock(GroupRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);

		service = new GroupLifecycleService(groupRepository, currentActorProvider, eventPublisher);
	}

	@Test
	@DisplayName("Should create group and enforce unique code per tenant")
	void shouldCreateGroup() {
		TenantId tenantId = TenantId.generate();
		when(groupRepository.existsByTenantIdAndCode(tenantId, "SURGERY")).thenReturn(false);
		when(groupRepository.save(any(Group.class))).thenAnswer(i -> i.getArgument(0));

		Group group = service.createGroup(new CreateGroupCommand(tenantId, "SURGERY", "Surgery", "Desc", "idp-surgery"));
		assertThat(group.getCode()).isEqualTo("SURGERY");

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.GROUP_CREATED);

		when(groupRepository.existsByTenantIdAndCode(tenantId, "SURGERY")).thenReturn(true);
		assertThatThrownBy(() -> service.createGroup(new CreateGroupCommand(tenantId, "SURGERY", "Surgery", "Desc", "idp-surgery")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should get group by ID, get by tenant, update, and delete group")
	void shouldPerformCrudOperations() {
		TenantId tenantId = TenantId.generate();
		Group group = Group.create(tenantId, "PEDIATRICS", "Pediatrics", "Desc", null);
		GroupId groupId = group.getId();

		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(groupRepository.findAllByTenantId(tenantId)).thenReturn(List.of(group));
		when(groupRepository.save(any(Group.class))).thenAnswer(i -> i.getArgument(0));

		Group found = service.getGroupById(groupId);
		assertThat(found).isEqualTo(group);

		List<Group> list = service.getGroupsByTenant(tenantId);
		assertThat(list).containsExactly(group);

		Group updated = service.updateGroup(new UpdateGroupCommand(groupId, "Peds New", "New Desc", "new-ext"));
		assertThat(updated.getName()).isEqualTo("Peds New");

		service.deleteGroup(groupId);
		verify(groupRepository).delete(groupId);

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher, times(2)).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.GROUP_DELETED);
	}

	@Test
	@DisplayName("Should throw GroupNotFoundException when group not found")
	void shouldThrowWhenGroupNotFound() {
		GroupId groupId = GroupId.generate();
		when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getGroupById(groupId))
				.isInstanceOf(GroupNotFoundException.class);
	}

	@Test
	@DisplayName("Should block cross-tenant access when not platform superadmin")
	void shouldBlockCrossTenantAccess() {
		TenantId actorTenantId = TenantId.generate();
		TenantId targetTenantId = TenantId.generate();

		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		Group targetGroup = Group.create(targetTenantId, "CARDIO", "Cardiology", "Desc", null);
		when(groupRepository.findById(targetGroup.getId())).thenReturn(Optional.of(targetGroup));

		assertThatThrownBy(() -> service.getGroupById(targetGroup.getId()))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied: operation not permitted for a different tenant.");
	}
}
