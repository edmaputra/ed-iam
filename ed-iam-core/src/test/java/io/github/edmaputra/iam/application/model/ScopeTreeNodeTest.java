package io.github.edmaputra.iam.application.model;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test verifying ScopeTreeNode hierarchical data structure and traversal operations.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class ScopeTreeNodeTest {

	@Test
	@DisplayName("Should build hierarchical n-ary tree from flat list of ScopeNodes")
	void shouldBuildTreeFromFlatList() {
		TenantId tenantId = TenantId.generate();

		ScopeNode hospital = ScopeNode.createRoot(tenantId, "HOSPITAL", "Central Hospital");
		ScopeNode inpatientDiv = ScopeNode.createChild(tenantId, hospital, "INPATIENT", "Inpatient Division");
		ScopeNode outpatientDiv = ScopeNode.createChild(tenantId, hospital, "OUTPATIENT", "Outpatient Division");
		ScopeNode cardiologyDept = ScopeNode.createChild(tenantId, outpatientDiv, "CARDIOLOGY", "Cardiology Dept");
		ScopeNode cardioClinic = ScopeNode.createChild(tenantId, cardiologyDept, "CARDIO_CLINIC_1", "Cardiology Clinic 1");

		List<ScopeNode> flatList = List.of(hospital, inpatientDiv, outpatientDiv, cardiologyDept, cardioClinic);

		List<ScopeTreeNode> tree = ScopeTreeNode.from(flatList);

		assertThat(tree).hasSize(1);
		ScopeTreeNode root = tree.getFirst();
		assertThat(root.code()).isEqualTo("HOSPITAL");
		assertThat(root.children()).hasSize(2);

		ScopeTreeNode inpatient = root.children().stream()
				.filter(c -> c.code().equals("INPATIENT"))
				.findFirst().orElseThrow();
		assertThat(inpatient.children()).isEmpty();

		ScopeTreeNode outpatient = root.children().stream()
				.filter(c -> c.code().equals("OUTPATIENT"))
				.findFirst().orElseThrow();
		assertThat(outpatient.children()).hasSize(1);

		ScopeTreeNode cardiology = outpatient.children().getFirst();
		assertThat(cardiology.code()).isEqualTo("CARDIOLOGY");
		assertThat(cardiology.children()).hasSize(1);

		ScopeTreeNode clinic = cardiology.children().getFirst();
		assertThat(clinic.code()).isEqualTo("CARDIO_CLINIC_1");
		assertThat(clinic.children()).isEmpty();
	}

	@Test
	@DisplayName("Should handle empty and null lists safely")
	void shouldHandleEmptyList() {
		assertThat(ScopeTreeNode.from(null)).isEmpty();
		assertThat(ScopeTreeNode.from(List.of())).isEmpty();
	}
}
