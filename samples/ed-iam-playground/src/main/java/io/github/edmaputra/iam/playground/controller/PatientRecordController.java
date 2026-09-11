package io.github.edmaputra.iam.playground.controller;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.playground.domain.PatientRecord;
import io.github.edmaputra.iam.playground.domain.PatientRecordRepository;

/**
 * Controller demonstrating scope-based and permission-based authorization
 * applied to a multi-tenant business domain.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/playground/patients")
@RequiredArgsConstructor
public class PatientRecordController {

	private final CurrentActorProvider currentActorProvider;
	private final PatientRecordRepository patientRecordRepository;

	@GetMapping
	public ResponseEntity<List<PatientRecord>> getPatientRecords(
			@RequestParam(required = false) UUID departmentId) {
		CurrentActor actor = currentActorProvider.requireCurrentActor();

		if (!actor.hasPermission("PATIENT_READ")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Missing required permission: PATIENT_READ");
		}

		if (departmentId != null) {
			if (!actor.canAccessScope(departmentId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,
						"Access to department scope [%s] is forbidden for actor [%s]".formatted(departmentId, actor.email()));
			}
			List<PatientRecord> records = patientRecordRepository.findByTenantIdAndDepartmentScopeId(actor.tenantId(), departmentId);
			return ResponseEntity.ok(records);
		}

		List<PatientRecord> allTenantRecords = patientRecordRepository.findByTenantId(actor.tenantId());
		if (actor.isTenantWide() || actor.isPlatformSuperAdmin()) {
			return ResponseEntity.ok(allTenantRecords);
		}

		List<PatientRecord> accessibleRecords = allTenantRecords.stream()
				.filter(r -> actor.canAccessScope(r.getDepartmentScopeId()))
				.toList();
		return ResponseEntity.ok(accessibleRecords);
	}

	@PostMapping
	public ResponseEntity<PatientRecord> createPatientRecord(@RequestBody CreatePatientRecordRequest request) {
		CurrentActor actor = currentActorProvider.requireCurrentActor();

		if (!actor.hasPermission("PATIENT_WRITE")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Missing required permission: PATIENT_WRITE");
		}

		Objects.requireNonNull(request.departmentScopeId(), "departmentScopeId must not be null.");
		if (!actor.canAccessScope(request.departmentScopeId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Actor [%s] is not permitted to create records in department scope [%s]".formatted(
							actor.email(), request.departmentScopeId()));
		}

		PatientRecord record = new PatientRecord(
				UUID.randomUUID(),
				actor.tenantId(),
				request.departmentScopeId(),
				request.departmentName(),
				request.patientName(),
				request.diagnosis(),
				request.treatmentNotes(),
				Instant.now()
		);

		PatientRecord saved = patientRecordRepository.save(record);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	public record CreatePatientRecordRequest(
			UUID departmentScopeId,
			String departmentName,
			String patientName,
			String diagnosis,
			String treatmentNotes
	) {}
}
