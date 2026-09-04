package io.github.edmaputra.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopeNodeTest {

	@Test
	@DisplayName("Should create root scope node with correct path format")
	void shouldCreateRootScopeNode() {
		TenantId tenantId = TenantId.generate();
		ScopeNode root = ScopeNode.createRoot(tenantId, "MAIN_HOSPITAL", "Main Hospital Facility");

		assertThat(root.getId()).isNotNull();
		assertThat(root.getTenantId()).isEqualTo(tenantId);
		assertThat(root.getParentId()).isNull();
		assertThat(root.optionalParentId()).isEmpty();
		assertThat(root.isRoot()).isTrue();
		assertThat(root.getCode()).isEqualTo("MAIN_HOSPITAL");
		assertThat(root.getName()).isEqualTo("Main Hospital Facility");

		String expectedPath = "/" + tenantId.value() + "/" + root.getId().value() + "/";
		assertThat(root.getPath()).isEqualTo(expectedPath);
	}

	@Test
	@DisplayName("Should create child scope node with appended path format")
	void shouldCreateChildScopeNode() {
		TenantId tenantId = TenantId.generate();
		ScopeNode root = ScopeNode.createRoot(tenantId, "MAIN", "Main Facility");
		ScopeNode surgeryDiv = ScopeNode.createChild(tenantId, root, "SURGERY", "Surgery Division");
		ScopeNode generalSurgeryDept = ScopeNode.createChild(tenantId, surgeryDiv, "GEN_SURGERY", "General Surgery Dept");

		assertThat(surgeryDiv.getParentId()).isEqualTo(root.getId());
		assertThat(surgeryDiv.isRoot()).isFalse();
		assertThat(surgeryDiv.getPath()).isEqualTo(root.getPath() + surgeryDiv.getId().value() + "/");

		assertThat(generalSurgeryDept.getParentId()).isEqualTo(surgeryDiv.getId());
		assertThat(generalSurgeryDept.getPath())
				.isEqualTo(root.getPath() + surgeryDiv.getId().value() + "/" + generalSurgeryDept.getId().value() + "/");
	}

	@Test
	@DisplayName("Should update metadata and support moving to new parent")
	void shouldUpdateMetadataAndMove() {
		TenantId tenantId = TenantId.generate();
		ScopeNode node = ScopeNode.createRoot(tenantId, "OLD_CODE", "Old Name");

		node.updateMetadata("NEW_CODE", "New Name");
		assertThat(node.getCode()).isEqualTo("NEW_CODE");
		assertThat(node.getName()).isEqualTo("New Name");

		ScopeNodeId newParentId = ScopeNodeId.generate();
		String newPath = "/" + tenantId.value() + "/" + newParentId.value() + "/" + node.getId().value() + "/";
		node.moveTo(newParentId, newPath);

		assertThat(node.getParentId()).isEqualTo(newParentId);
		assertThat(node.getPath()).isEqualTo(newPath);
	}

	@Test
	@DisplayName("Should validate path format")
	void shouldValidatePathFormat() {
		TenantId tenantId = TenantId.generate();
		assertThatThrownBy(() -> new ScopeNode(
				ScopeNodeId.generate(),
				tenantId,
				null,
				"CODE",
				"Name",
				"invalid-path-without-slashes",
				java.time.Instant.now(),
				java.time.Instant.now()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must start and end with '/'");
	}
}
