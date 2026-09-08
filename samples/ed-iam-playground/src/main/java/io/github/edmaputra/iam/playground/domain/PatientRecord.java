package io.github.edmaputra.iam.playground.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sample domain entity demonstrating a tenant-owned and scope-bounded business resource.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Entity
@Table(name = "playground_patient_record")
@Getter
@Setter
@NoArgsConstructor
public class PatientRecord {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private UUID departmentScopeId;

	@Column(nullable = false)
	private String departmentName;

	@Column(nullable = false)
	private String patientName;

	@Column(nullable = false)
	private String diagnosis;

	@Column(length = 1000)
	private String treatmentNotes;

	@Column(nullable = false)
	private Instant createdAt;

	public PatientRecord(UUID id, UUID tenantId, UUID departmentScopeId, String departmentName,
			String patientName, String diagnosis, String treatmentNotes, Instant createdAt) {
		this.id = id;
		this.tenantId = tenantId;
		this.departmentScopeId = departmentScopeId;
		this.departmentName = departmentName;
		this.patientName = patientName;
		this.diagnosis = diagnosis;
		this.treatmentNotes = treatmentNotes;
		this.createdAt = createdAt;
	}
}
