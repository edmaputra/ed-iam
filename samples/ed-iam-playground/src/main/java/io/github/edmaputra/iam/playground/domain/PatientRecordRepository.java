package io.github.edmaputra.iam.playground.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PatientRecord}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Repository
public interface PatientRecordRepository extends JpaRepository<PatientRecord, UUID> {

	List<PatientRecord> findByTenantIdAndDepartmentScopeId(UUID tenantId, UUID departmentScopeId);

	List<PatientRecord> findByTenantId(UUID tenantId);
}
