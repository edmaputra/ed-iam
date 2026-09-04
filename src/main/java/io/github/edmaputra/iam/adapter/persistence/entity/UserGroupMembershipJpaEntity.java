package io.github.edmaputra.iam.adapter.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a user-group membership in the {@code iam_user_group_membership} table.
 *
 * @author edmaputra
 */
@Entity
@Table(name = "iam_user_group_membership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupMembershipJpaEntity {

	@EmbeddedId
	private UserGroupMembershipJpaId id;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;
}
