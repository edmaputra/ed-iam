package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.model.ScopeTreeNode;
import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.DeleteScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.in.MoveScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.UpdateScopeNodeCommand;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.ScopeNodeNotFoundException;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;

/**
 * Application service implementing {@link ManageScopeUseCase}.
 * Manages scope tree hierarchy, path generation, cycle detection, re-parenting cascades, and audit events.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class ScopeHierarchyService implements ManageScopeUseCase {

	private final ScopeNodeRepository scopeNodeRepository;
	private final EventPublisherPort eventPublisher;

	@Override
	@Transactional
	public ScopeNode createScopeNode(CreateScopeNodeCommand command, OperationContext context) {
		Objects.requireNonNull(command, "Command must not be null.");
		validateCodeUniqueness(command.tenantId(), command.code());

		ScopeNode node;
		if (command.isRoot()) {
			node = ScopeNode.createRoot(command.tenantId(), command.code(), command.name());
		}
		else {
			ScopeNode parent = getById(command.tenantId(), command.parentId());
			node = ScopeNode.createChild(command.tenantId(), parent, command.code(), command.name());
		}

		ScopeNode saved = scopeNodeRepository.save(node);
		publishEvent(IamEventTypes.SCOPE_NODE_CREATED, command.tenantId(), saved.getId(), saved, context);
		return saved;
	}

	@Override
	@Transactional
	public ScopeNode updateMetadata(UpdateScopeNodeCommand command, OperationContext context) {
		Objects.requireNonNull(command, "Command must not be null.");
		ScopeNode node = getById(command.tenantId(), command.id());

		if (!node.getCode().equalsIgnoreCase(command.code())) {
			validateCodeUniqueness(command.tenantId(), command.code());
		}

		node.updateMetadata(command.code(), command.name());
		ScopeNode updated = scopeNodeRepository.save(node);

		publishEvent(IamEventTypes.SCOPE_NODE_UPDATED, command.tenantId(), updated.getId(), updated, context);
		return updated;
	}

	@Override
	@Transactional
	public ScopeNode moveNode(MoveScopeNodeCommand command, OperationContext context) {
		Objects.requireNonNull(command, "Command must not be null.");

		ScopeNode target = getById(command.tenantId(), command.id());
		ScopeNode newParent = getById(command.tenantId(), command.newParentId());

		// Cycle detection: Cannot move a node under one of its own descendants
		if (newParent.getPath().startsWith(target.getPath())) {
			throw new IllegalArgumentException(
					"Cannot move scope node '" + target.getCode() + "' under its descendant '" + newParent.getCode() + "' (cycle detected).");
		}

		String oldPath = target.getPath();
		String newPath = newParent.getPath() + target.getId().value() + "/";

		target.moveTo(newParent.getId(), newPath);
		ScopeNode saved = scopeNodeRepository.save(target);

		// Cascading update to all descendant nodes
		scopeNodeRepository.updatePathPrefix(oldPath, newPath);

		publishEvent(
				IamEventTypes.SCOPE_NODE_MOVED,
				command.tenantId(),
				saved.getId(),
				Map.of("oldPath", oldPath, "newPath", newPath, "newParentId", command.newParentId().value().toString()),
				context);

		return saved;
	}

	@Override
	@Transactional
	public void deleteNode(DeleteScopeNodeCommand command, OperationContext context) {
		Objects.requireNonNull(command, "Command must not be null.");
		ScopeNode node = getById(command.tenantId(), command.id());

		if (scopeNodeRepository.existsByParentId(command.id())) {
			throw new IllegalStateException("Cannot delete scope node '" + node.getCode() + "' because it has child nodes.");
		}

		scopeNodeRepository.delete(command.id());
		publishEvent(IamEventTypes.SCOPE_NODE_DELETED, command.tenantId(), command.id(), null, context);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScopeTreeNode> getScopeTree(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		List<ScopeNode> nodes = scopeNodeRepository.findAllByTenantId(tenantId);
		return ScopeTreeNode.from(nodes);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScopeNode> getFlatScopeList(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return scopeNodeRepository.findAllByTenantId(tenantId);
	}

	@Override
	@Transactional(readOnly = true)
	public ScopeNode getById(TenantId tenantId, ScopeNodeId id) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(id, "ScopeNodeId must not be null.");

		return scopeNodeRepository.findById(id)
				.filter(n -> n.getTenantId().equals(tenantId))
				.orElseThrow(() -> new ScopeNodeNotFoundException(id));
	}

	private void validateCodeUniqueness(TenantId tenantId, String code) {
		if (scopeNodeRepository.existsByTenantIdAndCode(tenantId, code.trim().toUpperCase())) {
			throw new IllegalArgumentException("A scope node with code '" + code + "' already exists for this tenant.");
		}
	}

	private void publishEvent(
			String eventType,
			TenantId tenantId,
			ScopeNodeId entityId,
			Object payload,
			OperationContext context) {
		IamEvent event = IamEvent.of(
				eventType,
				tenantId.value(),
				entityId.value(),
				"SCOPE_NODE",
				payload,
				context);
		eventPublisher.publish(event);
	}
}
