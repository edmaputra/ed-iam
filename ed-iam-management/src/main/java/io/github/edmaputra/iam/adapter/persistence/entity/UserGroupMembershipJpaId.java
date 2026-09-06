package io.github.edmaputra.iam.adapter.persistence.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite embedded ID for {@link UserGroupMembershipJpaEntity}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserGroupMembershipJpaId implements Serializable {

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;
}
