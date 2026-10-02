package io.github.edmaputra.iam.playground.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.playground.config.HostTenantContext;
import io.github.edmaputra.iam.playground.domain.PatientRecordRepository;
import io.github.edmaputra.iam.playground.seeder.PlaygroundDataSeeder;

/**
 * Controller exposing dynamic, database-backed scope hierarchy tree nodes and
 * real-time access evaluation for the active tenant and persona.
 *
 * @author edmaputra
 * @since 0.6.0
 */
@RestController
@RequestMapping("/api/v1/playground/scopes")
@RequiredArgsConstructor
public class PlaygroundScopeController {

	private final ScopeNodeRepository scopeNodeRepository;
	private final PatientRecordRepository patientRecordRepository;
	private final CurrentActorProvider currentActorProvider;

	@GetMapping("/tree")
	public ResponseEntity<PlaygroundScopeOverviewResponse> getScopeTree(
			@RequestParam(required = false) UUID tenantId) {
		UUID resolvedTenantId = resolveTenantId(tenantId);
		List<ScopeNode> allNodes = scopeNodeRepository.findAllByTenantId(new TenantId(resolvedTenantId));

		Optional<CurrentActor> actorOpt = currentActorProvider.currentActor();
		boolean authenticated = actorOpt.isPresent();
		String actorEmail = actorOpt.map(CurrentActor::email).orElse("anonymous");
		String actorRoleSummary = actorOpt.map(a -> String.join(", ", a.roles())).orElse("None");

		List<PlaygroundScopeNodeDto> flatList = new ArrayList<>();
		for (ScopeNode node : allNodes) {
			int count = patientRecordRepository.findByTenantIdAndDepartmentScopeId(resolvedTenantId, node.getId().value()).size();
			ScopeAccessEvaluation eval = evaluateAccess(node, resolvedTenantId, actorOpt);
			flatList.add(new PlaygroundScopeNodeDto(
					node.getId().value(),
					node.getTenantId().value(),
					node.getParentId() != null ? node.getParentId().value() : null,
					node.getCode(),
					node.getName(),
					node.getPath(),
					count,
					eval.accessible(),
					eval.accessStatus(),
					eval.accessStatusLabel(),
					eval.colorTheme(),
					new ArrayList<>()
			));
		}

		flatList.sort(Comparator.comparing(PlaygroundScopeNodeDto::path));
		List<PlaygroundScopeNodeDto> tree = assembleTree(flatList);
		String tenantName = resolveTenantName(resolvedTenantId);

		return ResponseEntity.ok(new PlaygroundScopeOverviewResponse(
				resolvedTenantId,
				tenantName,
				authenticated,
				actorEmail,
				actorRoleSummary,
				tree,
				flatList
		));
	}

	private List<PlaygroundScopeNodeDto> assembleTree(List<PlaygroundScopeNodeDto> flatList) {
		Map<UUID, List<PlaygroundScopeNodeDto>> childrenByParent = flatList.stream()
				.filter(n -> n.parentId() != null)
				.collect(Collectors.groupingBy(PlaygroundScopeNodeDto::parentId));

		Map<UUID, PlaygroundScopeNodeDto> nodeMap = flatList.stream()
				.collect(Collectors.toMap(PlaygroundScopeNodeDto::id, n -> n));

		return flatList.stream()
				.filter(n -> n.parentId() == null || !nodeMap.containsKey(n.parentId()))
				.map(root -> buildNodeWithChildren(root, childrenByParent))
				.toList();
	}

	private PlaygroundScopeNodeDto buildNodeWithChildren(
			PlaygroundScopeNodeDto node,
			Map<UUID, List<PlaygroundScopeNodeDto>> childrenByParent) {
		List<PlaygroundScopeNodeDto> rawChildren = childrenByParent.getOrDefault(node.id(), List.of());
		List<PlaygroundScopeNodeDto> populatedChildren = rawChildren.stream()
				.map(child -> buildNodeWithChildren(child, childrenByParent))
				.toList();

		return new PlaygroundScopeNodeDto(
				node.id(),
				node.tenantId(),
				node.parentId(),
				node.code(),
				node.name(),
				node.path(),
				node.patientCount(),
				node.accessible(),
				node.accessStatus(),
				node.accessStatusLabel(),
				node.colorTheme(),
				populatedChildren
		);
	}

	private ScopeAccessEvaluation evaluateAccess(
			ScopeNode node,
			UUID resolvedTenantId,
			Optional<CurrentActor> actorOpt) {
		if (actorOpt.isEmpty()) {
			return new ScopeAccessEvaluation(false, "ANONYMOUS", "Login Required", "gray");
		}
		CurrentActor actor = actorOpt.get();
		if (!actor.isPlatformSuperAdmin() && !resolvedTenantId.equals(actor.tenantId())) {
			return new ScopeAccessEvaluation(false, "WRONG_TENANT", "403 Forbidden (Cross-Tenant)", "red");
		}
		if (!actor.hasPermission("PATIENT_READ")) {
			return new ScopeAccessEvaluation(false, "NO_PERMISSION", "403 Forbidden (No PATIENT_READ)", "red");
		}
		if (actor.isPlatformSuperAdmin()) {
			return new ScopeAccessEvaluation(true, "SUPER_ADMIN", "Expected: 200 OK (Super Admin)", "emerald");
		}
		if (actor.isTenantWide()) {
			return new ScopeAccessEvaluation(true, "TENANT_WIDE", "Expected: 200 OK (Tenant-Wide)", "emerald");
		}
		UUID nodeId = node.getId().value();
		if (actor.canAccessScope(nodeId)) {
			boolean isInherited = isInheritedScope(node, actor);
			String status = isInherited ? "INHERITED" : "DIRECT";
			String label = isInherited ? "Expected: 200 OK (Inherited)" : "Expected: 200 OK";
			return new ScopeAccessEvaluation(true, status, label, "emerald");
		}
		return new ScopeAccessEvaluation(false, "FORBIDDEN", "Expected: 403 Forbidden", "red");
	}

	private boolean isInheritedScope(ScopeNode node, CurrentActor actor) {
		ScopeNodeId parentId = node.getParentId();
		while (parentId != null) {
			if (actor.canAccessScope(parentId.value())) {
				return true;
			}
			Optional<ScopeNode> parentOpt = scopeNodeRepository.findById(parentId);
			if (parentOpt.isPresent()) {
				parentId = parentOpt.get().getParentId();
			} else {
				break;
			}
		}
		return false;
	}

	private UUID resolveTenantId(UUID requestedTenantId) {
		if (requestedTenantId != null) {
			return requestedTenantId;
		}
		Optional<CurrentActor> actorOpt = currentActorProvider.currentActor();
		if (actorOpt.isPresent()) {
			return actorOpt.get().tenantId();
		}
		return HostTenantContext.getTenantId()
				.orElse(PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID);
	}

	private String resolveTenantName(UUID tenantId) {
		if (PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.equals(tenantId)) {
			return "Metro General Hospital";
		}
		if (PlaygroundDataSeeder.ST_JUDE_TENANT_ID.equals(tenantId)) {
			return "St. Jude Medical Center";
		}
		return "Tenant " + tenantId.toString().substring(0, 8);
	}

	private record ScopeAccessEvaluation(
			boolean accessible,
			String accessStatus,
			String accessStatusLabel,
			String colorTheme
	) {}

	public record PlaygroundScopeNodeDto(
			UUID id,
			UUID tenantId,
			UUID parentId,
			String code,
			String name,
			String path,
			int patientCount,
			boolean accessible,
			String accessStatus,
			String accessStatusLabel,
			String colorTheme,
			List<PlaygroundScopeNodeDto> children
	) {}

	public record PlaygroundScopeOverviewResponse(
			UUID tenantId,
			String tenantName,
			boolean authenticated,
			String actorEmail,
			String actorRoleSummary,
			List<PlaygroundScopeNodeDto> tree,
			List<PlaygroundScopeNodeDto> flatList
	) {}
}
