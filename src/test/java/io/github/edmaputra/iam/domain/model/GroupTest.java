package io.github.edmaputra.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

class GroupTest {

	@Test
	@DisplayName("Should create group with details and membership")
	void shouldCreateGroup() {
		TenantId tenantId = TenantId.generate();
		Group group = Group.create(
				tenantId,
				"CARDIOLOGY_FELLOWS",
				"Cardiology Fellows 2026",
				"On-call cardiology fellows",
				"ADFS-Cardiology-Fellows");

		assertThat(group.getId()).isNotNull();
		assertThat(group.getTenantId()).isEqualTo(tenantId);
		assertThat(group.getCode()).isEqualTo("CARDIOLOGY_FELLOWS");
		assertThat(group.getName()).isEqualTo("Cardiology Fellows 2026");
		assertThat(group.optionalDescription()).contains("On-call cardiology fellows");
		assertThat(group.optionalExternalIdpGroupName()).contains("ADFS-Cardiology-Fellows");

		group.updateDetails("Cardiology Fellows 2026-2027", "Updated", null);
		assertThat(group.getName()).isEqualTo("Cardiology Fellows 2026-2027");
		assertThat(group.optionalExternalIdpGroupName()).isEmpty();
	}

	@Test
	@DisplayName("Should create UserGroupMembership")
	void shouldCreateMembership() {
		GroupId groupId = GroupId.generate();
		UserId userId = UserId.generate();
		UserGroupMembership membership = UserGroupMembership.of(groupId, userId);

		assertThat(membership.groupId()).isEqualTo(groupId);
		assertThat(membership.userId()).isEqualTo(userId);
		assertThat(membership.joinedAt()).isNotNull();
	}
}
