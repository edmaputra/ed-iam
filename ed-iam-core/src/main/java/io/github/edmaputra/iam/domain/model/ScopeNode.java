package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantOwned;
import lombok.Getter;

/**
 * Pure domain entity representing a hierarchical organizational unit or facility scope node.
 * Uses materialized paths (e.g. {@code /<tenantId>/<rootId>/<childId>/}) for high-performance subtree inheritance.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@Getter
public class ScopeNode implements TenantOwned {

	private final ScopeNodeId id;
	private final TenantId tenantId;
	private ScopeNodeId parentId;
	private String code;
	private String name;
	private String path;
	private final Instant createdAt;
	private Instant updatedAt;

	/**
	 * Canonical constructor for reconstructing scope nodes from persistence.
	 *
	 * @param id        the unique scope node ID
	 * @param tenantId  the owning tenant ID
	 * @param parentId  the parent scope node ID (null for root nodes)
	 * @param code      the uppercase unique code within parent
	 * @param name      the human-readable node name
	 * @param path      the materialized hierarchy path
	 * @param createdAt creation timestamp
	 * @param updatedAt last updated timestamp
	 */
	public ScopeNode(
			ScopeNodeId id,
			TenantId tenantId,
			ScopeNodeId parentId,
			String code,
			String name,
			String path,
			Instant createdAt,
			Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "ScopeNode ID must not be null.");
		this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null.");
		this.parentId = parentId; // Nullable for root nodes
		this.code = validateCode(code);
		this.name = validateName(name);
		this.path = validatePath(path);
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
		this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt must not be null.");
	}

	/**
	 * Factory method creating a root scope node for a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the scope node code
	 * @param name     the scope node name
	 * @return new root {@link ScopeNode}
	 */
	public static ScopeNode createRoot(
			TenantId tenantId,
			String code,
			String name) {
		ScopeNodeId nodeId = ScopeNodeId.generate();
		String path = "/" + tenantId.value() + "/" + nodeId.value() + "/";
		Instant now = Instant.now();
		return new ScopeNode(
				nodeId,
				tenantId,
				null,
				code,
				name,
				path,
				now,
				now);
	}

	/**
	 * Factory method creating a child scope node under an existing parent.
	 *
	 * @param tenantId the tenant ID
	 * @param parent   the parent scope node
	 * @param code     the child scope node code
	 * @param name     the child scope node name
	 * @return new child {@link ScopeNode}
	 */
	public static ScopeNode createChild(
			TenantId tenantId,
			ScopeNode parent,
			String code,
			String name) {
		Objects.requireNonNull(parent, "Parent ScopeNode must not be null.");
		ScopeNodeId nodeId = ScopeNodeId.generate();
		String path = parent.getPath() + nodeId.value() + "/";
		Instant now = Instant.now();
		return new ScopeNode(
				nodeId,
				tenantId,
				parent.getId(),
				code,
				name,
				path,
				now,
				now);
	}

	@Override
	public TenantId tenantId() {
		return this.tenantId;
	}

	/**
	 * Updates the node's code and name.
	 *
	 * @param code the updated code
	 * @param name the updated name
	 */
	public void updateMetadata(String code, String name) {
		this.code = validateCode(code);
		this.name = validateName(name);
		this.updatedAt = Instant.now();
	}

	/**
	 * Re-parents this node and updates its materialized path.
	 *
	 * @param newParentId the new parent scope node ID (or null for root)
	 * @param newPath     the new computed materialized path
	 */
	public void moveTo(ScopeNodeId newParentId, String newPath) {
		this.parentId = newParentId;
		this.path = validatePath(newPath);
		this.updatedAt = Instant.now();
	}

	/**
	 * Returns the optional parent node ID.
	 *
	 * @return optional {@link ScopeNodeId}
	 */
	public Optional<ScopeNodeId> optionalParentId() {
		return Optional.ofNullable(parentId);
	}

	/**
	 * Returns true if this is a top-level root scope node.
	 *
	 * @return true if root
	 */
	public boolean isRoot() {
		return this.parentId == null;
	}

	private static String validateCode(String code) {
		Objects.requireNonNull(code, "ScopeNode code must not be null.");
		String trimmed = code.trim().toUpperCase();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("ScopeNode code must not be blank.");
		}
		return trimmed;
	}

	private static String validateName(String name) {
		Objects.requireNonNull(name, "ScopeNode name must not be null.");
		String trimmed = name.trim();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("ScopeNode name must not be blank.");
		}
		return trimmed;
	}

	private static String validatePath(String path) {
		Objects.requireNonNull(path, "ScopeNode path must not be null.");
		String trimmed = path.trim();
		if (trimmed.isBlank() || !trimmed.startsWith("/") || !trimmed.endsWith("/")) {
			throw new IllegalArgumentException("ScopeNode path must start and end with '/': " + path);
		}
		return trimmed;
	}
}
