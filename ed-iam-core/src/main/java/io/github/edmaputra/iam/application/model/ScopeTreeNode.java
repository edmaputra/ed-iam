package io.github.edmaputra.iam.application.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.model.ScopeNode;

/**
 * Representation of a scope hierarchy tree node with recursive children.
 *
 * @param id       the node UUID
 * @param code     the unique node code
 * @param name     the human-readable name
 * @param path     the materialized hierarchy path
 * @param children list of child tree nodes
 * @author edmaputra
 * @since 0.0.1
 */
public record ScopeTreeNode(
		UUID id,
		String code,
		String name,
		String path,
		List<ScopeTreeNode> children) {

	public ScopeTreeNode {
		Objects.requireNonNull(id, "ID must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		Objects.requireNonNull(name, "Name must not be null.");
		Objects.requireNonNull(path, "Path must not be null.");
		children = children == null ? List.of() : List.copyOf(children);
	}

	/**
	 * Builds a recursive tree representation from a flat list of scope nodes.
	 *
	 * @param nodes the flat list of nodes
	 * @return list of root scope tree nodes with nested children
	 */
	public static List<ScopeTreeNode> from(List<ScopeNode> nodes) {
		if (nodes == null || nodes.isEmpty()) {
			return List.of();
		}

		Map<UUID, List<ScopeNode>> childrenByParent = new LinkedHashMap<>();
		List<ScopeNode> roots = new ArrayList<>();

		for (ScopeNode node : nodes) {
			if (node.isRoot()) {
				roots.add(node);
			}
			else {
				childrenByParent.computeIfAbsent(node.getParentId().value(), k -> new ArrayList<>()).add(node);
			}
		}

		return roots.stream()
				.map(root -> buildNode(root, childrenByParent))
				.toList();
	}

	private static ScopeTreeNode buildNode(ScopeNode node, Map<UUID, List<ScopeNode>> childrenByParent) {
		List<ScopeNode> children = childrenByParent.getOrDefault(node.getId().value(), List.of());
		List<ScopeTreeNode> childNodes = children.stream()
				.map(child -> buildNode(child, childrenByParent))
				.toList();

		return new ScopeTreeNode(
				node.getId().value(),
				node.getCode(),
				node.getName(),
				node.getPath(),
				childNodes);
	}
}
