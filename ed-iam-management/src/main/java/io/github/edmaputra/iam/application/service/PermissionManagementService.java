package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.in.ManagePermissionUseCase;
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
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link ManagePermissionUseCase} for catalog and custom permission management.
 *
 * @author edmaputra
 * @since 0.10.0
 */
public class PermissionManagementService implements ManagePermissionUseCase {

	private final PermissionRepository permissionRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public PermissionManagementService(
			PermissionRepository permissionRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.permissionRepository = Objects.requireNonNull(permissionRepository, "PermissionRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public PermissionManagementService(
			PermissionRepository permissionRepository,
			CurrentActorProvider currentActorProvider) {
		this(permissionRepository, currentActorProvider, null);
	}

	public PermissionManagementService(PermissionRepository permissionRepository) {
		this(permissionRepository, null, null);
	}

	@Override
	@Transactional
	public Permission createPermission(CreatePermissionCommand command) {
		Objects.requireNonNull(command, "CreatePermissionCommand must not be null.");

		if (command.tenantId() != null) {
			checkTenantAccess(command.tenantId());
			if (permissionRepository.existsByTenantIdAndCode(command.tenantId(), command.code())) {
				throw new IllegalArgumentException("Permission code already exists for tenant: " + command.code());
			}
		}
		else {
			checkSuperAdminAccess("Only platform superadmins can create global system permissions.");
			if (permissionRepository.existsSystemPermissionByCode(command.code())) {
				throw new IllegalArgumentException("System permission code already exists: " + command.code());
			}
		}

		Permission permission = command.tenantId() != null
				? Permission.createCustom(
						command.tenantId(),
						command.code(),
						command.name(),
						command.description(),
						command.category())
				: Permission.createSystem(
						command.code(),
						command.name(),
						command.description(),
						command.category());

		Permission saved = permissionRepository.save(permission);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.PERMISSION_CREATED,
					saved.tenantId() != null ? saved.tenantId().value() : null,
					saved.id().value(),
					"PERMISSION",
					Map.of("code", saved.code(), "name", saved.name(), "category", saved.category()),
					actorStr));
		}
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public Permission getPermissionById(PermissionId id) {
		Objects.requireNonNull(id, "PermissionId must not be null.");
		Permission permission = permissionRepository.findById(id)
				.orElseThrow(() -> new PermissionNotFoundException(id));
		if (permission.tenantId() != null) {
			checkTenantAccess(permission.tenantId());
		}
		return permission;
	}

	@Override
	@Transactional(readOnly = true)
	public Permission getPermissionByCode(TenantId tenantId, String code) {
		Objects.requireNonNull(code, "Code must not be null.");
		if (tenantId != null) {
			checkTenantAccess(tenantId);
		}
		return permissionRepository.findByTenantIdOrGlobalByCode(tenantId, code)
				.orElseThrow(() -> new PermissionNotFoundException(code));
	}

	@Override
	@Transactional(readOnly = true)
	public List<Permission> getPermissions(TenantId tenantId, String category) {
		if (tenantId != null) {
			checkTenantAccess(tenantId);
			if (category != null && !category.isBlank()) {
				return permissionRepository.findAllByTenantIdOrGlobalAndCategory(tenantId, category.trim());
			}
			return permissionRepository.findAllByTenantIdOrGlobal(tenantId);
		}

		if (category != null && !category.isBlank()) {
			return permissionRepository.findAllGlobalAndCategory(category.trim());
		}
		return permissionRepository.findAllGlobal();
	}

	@Override
	@Transactional
	public Permission updatePermission(UpdatePermissionCommand command) {
		Objects.requireNonNull(command, "UpdatePermissionCommand must not be null.");
		Permission permission = getPermissionById(command.permissionId());
		if (permission.systemPermission()) {
			throw new IllegalStateException("System permission cannot be modified: " + permission.code());
		}

		Permission updated = permission.update(command.name(), command.description(), command.category());
		Permission saved = permissionRepository.save(updated);

		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.PERMISSION_UPDATED,
					saved.tenantId() != null ? saved.tenantId().value() : null,
					saved.id().value(),
					"PERMISSION",
					Map.of("code", saved.code(), "name", saved.name(), "category", saved.category()),
					actorStr));
		}
		return saved;
	}

	@Override
	@Transactional
	public void deletePermission(PermissionId id) {
		Objects.requireNonNull(id, "PermissionId must not be null.");
		Permission permission = getPermissionById(id);
		if (permission.systemPermission()) {
			throw new IllegalStateException("System permission cannot be deleted: " + permission.code());
		}
		permissionRepository.delete(id);

		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.PERMISSION_DELETED,
					permission.tenantId() != null ? permission.tenantId().value() : null,
					permission.id().value(),
					"PERMISSION",
					Map.of("code", permission.code()),
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

	private void checkSuperAdminAccess(String message) {
		if (currentActorProvider == null) {
			return;
		}
		currentActorProvider.currentActor().ifPresent(actor -> {
			if (!actor.isPlatformSuperAdmin()) {
				throw new AccessDeniedException(message);
			}
		});
	}
}
