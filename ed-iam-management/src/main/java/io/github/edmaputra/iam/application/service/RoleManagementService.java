package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.in.ManageRoleUseCase;
import io.github.edmaputra.iam.application.port.in.RoleCommands.CreateRoleCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.UpdateRoleCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link ManageRoleUseCase} for custom role catalog management.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class RoleManagementService implements ManageRoleUseCase {

	private final RoleRepository roleRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public RoleManagementService(
			RoleRepository roleRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public RoleManagementService(RoleRepository roleRepository, CurrentActorProvider currentActorProvider) {
		this(roleRepository, currentActorProvider, null);
	}

	public RoleManagementService(RoleRepository roleRepository) {
		this(roleRepository, null, null);
	}

	@Override
	@Transactional
	public Role createRole(CreateRoleCommand command) {
		Objects.requireNonNull(command, "CreateRoleCommand must not be null.");
		checkTenantAccess(command.tenantId());

		if (roleRepository.existsByTenantIdAndCode(command.tenantId(), command.code())) {
			throw new IllegalArgumentException("Role code already exists for tenant: " + command.code());
		}

		Role role = Role.createCustom(
				command.tenantId(),
				command.code(),
				command.name(),
				command.description(),
				command.permissions());

		Role saved = roleRepository.save(role);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ROLE_CREATED,
					saved.getTenantId() != null ? saved.getTenantId().value() : null,
					saved.getId().value(),
					"ROLE",
					Map.of("code", saved.getCode(), "name", saved.getName()),
					actorStr));
		}
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public Role getRoleById(RoleId id) {
		Objects.requireNonNull(id, "RoleId must not be null.");
		Role role = roleRepository.findById(id)
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + id.value()));
		if (role.getTenantId() != null) {
			checkTenantAccess(role.getTenantId());
		}
		return role;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Role> getRolesByTenant(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		checkTenantAccess(tenantId);
		return roleRepository.findAllByTenantIdOrGlobal(tenantId);
	}

	@Override
	@Transactional
	public Role updateRole(UpdateRoleCommand command) {
		Objects.requireNonNull(command, "UpdateRoleCommand must not be null.");
		Role role = getRoleById(command.roleId());
		if (role.isSystemRole()) {
			throw new IllegalStateException("System role cannot be modified: " + role.getCode());
		}
		role.update(command.name(), command.description(), command.permissions());
		Role saved = roleRepository.save(role);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ROLE_MODIFIED,
					saved.getTenantId() != null ? saved.getTenantId().value() : null,
					saved.getId().value(),
					"ROLE",
					Map.of("code", saved.getCode(), "name", saved.getName(), "permissions", saved.getPermissions()),
					actorStr));
		}
		return saved;
	}

	@Override
	@Transactional
	public void deleteRole(RoleId id) {
		Objects.requireNonNull(id, "RoleId must not be null.");
		Role role = getRoleById(id);
		if (role.isSystemRole()) {
			throw new IllegalStateException("System role cannot be deleted: " + role.getCode());
		}
		roleRepository.delete(id);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ROLE_DELETED,
					role.getTenantId() != null ? role.getTenantId().value() : null,
					role.getId().value(),
					"ROLE",
					Map.of("code", role.getCode()),
					actorStr));
		}
	}

	private String resolveActor() {
		return currentActorProvider != null && currentActorProvider.currentActor().isPresent() &&
				currentActorProvider.currentActor().get().userId() != null
				? currentActorProvider.currentActor().get().userId().toString()
				: "system";
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
}
