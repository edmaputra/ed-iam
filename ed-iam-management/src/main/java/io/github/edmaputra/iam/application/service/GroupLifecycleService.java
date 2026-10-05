package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

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
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Focused application service responsible for group entity lifecycle and tenant-scoped CRUD operations.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class GroupLifecycleService {

	private final GroupRepository groupRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public GroupLifecycleService(
			GroupRepository groupRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.groupRepository = Objects.requireNonNull(groupRepository, "GroupRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public GroupLifecycleService(GroupRepository groupRepository) {
		this(groupRepository, null, null);
	}

	@Transactional
	public Group createGroup(CreateGroupCommand command) {
		Objects.requireNonNull(command, "CreateGroupCommand must not be null.");
		checkTenantAccess(command.tenantId());

		if (groupRepository.existsByTenantIdAndCode(command.tenantId(), command.code())) {
			throw new IllegalArgumentException("Group code already exists for tenant: " + command.code());
		}

		Group group = Group.create(
				command.tenantId(),
				command.code(),
				command.name(),
				command.description(),
				command.externalIdpGroupName());

		Group saved = groupRepository.save(group);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.GROUP_CREATED,
					saved.getTenantId() != null ? saved.getTenantId().value() : null,
					saved.getId().value(),
					"GROUP",
					Map.of("code", saved.getCode(), "name", saved.getName()),
					actorStr));
		}
		return saved;
	}

	@Transactional(readOnly = true)
	public Group getGroupById(GroupId id) {
		Objects.requireNonNull(id, "GroupId must not be null.");
		Group group = groupRepository.findById(id)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + id.value()));
		checkTenantAccess(group.getTenantId());
		return group;
	}

	@Transactional(readOnly = true)
	public List<Group> getGroupsByTenant(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		checkTenantAccess(tenantId);
		return groupRepository.findAllByTenantId(tenantId);
	}

	@Transactional
	public Group updateGroup(UpdateGroupCommand command) {
		Objects.requireNonNull(command, "UpdateGroupCommand must not be null.");
		Group group = getGroupById(command.groupId());
		group.updateDetails(command.name(), command.description(), command.externalIdpGroupName());
		Group saved = groupRepository.save(group);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.GROUP_UPDATED,
					saved.getTenantId() != null ? saved.getTenantId().value() : null,
					saved.getId().value(),
					"GROUP",
					Map.of("code", saved.getCode(), "name", saved.getName()),
					actorStr));
		}
		return saved;
	}

	@Transactional
	public void deleteGroup(GroupId id) {
		Objects.requireNonNull(id, "GroupId must not be null.");
		Group group = getGroupById(id);
		groupRepository.delete(id);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.GROUP_DELETED,
					group.getTenantId() != null ? group.getTenantId().value() : null,
					group.getId().value(),
					"GROUP",
					Map.of("code", group.getCode()),
					actorStr));
		}
	}

	private void checkTenantAccess(TenantId targetTenantId) {
		if (targetTenantId == null || currentActorProvider == null) {
			return;
		}
		currentActorProvider.currentActor().ifPresent(actor -> {
			if (!actor.isPlatformSuperAdmin()) {
				if (actor.tenantId() == null || !actor.tenantId().equals(targetTenantId.value())) {
					throw new AccessDeniedException("Access denied: operation not permitted for a different tenant.");
				}
			}
		});
	}

	private String resolveActor() {
		return currentActorProvider != null && currentActorProvider.currentActor().isPresent() &&
				currentActorProvider.currentActor().get().userId() != null
				? currentActorProvider.currentActor().get().userId().toString()
				: "system";
	}
}
