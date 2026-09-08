package io.github.edmaputra.iam.playground.seeder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.playground.domain.PatientRecord;
import io.github.edmaputra.iam.playground.domain.PatientRecordRepository;

/**
 * Automatically seeds demo tenants, hierarchical scope trees, users, roles,
 * and clinical patient records upon application startup.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Component
public class PlaygroundDataSeeder {

	private static final Logger log = LoggerFactory.getLogger(PlaygroundDataSeeder.class);

	// Multi-tenant Organization IDs
	public static final UUID METRO_HOSPITAL_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	public static final UUID ST_JUDE_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	// Metro General Hospital Hierarchical Scope IDs
	public static final UUID METRO_ROOT_SCOPE_ID = UUID.fromString("aaaa1111-0000-0000-0000-000000000001");
	public static final UUID CARDIOLOGY_SCOPE_ID = UUID.fromString("aaaa1111-0000-0000-0000-000000000002");
	public static final UUID ICU_SCOPE_ID = UUID.fromString("aaaa1111-0000-0000-0000-000000000003");
	public static final UUID PEDIATRICS_SCOPE_ID = UUID.fromString("aaaa1111-0000-0000-0000-000000000004");

	// St. Jude Scope ID
	public static final UUID ST_JUDE_ROOT_SCOPE_ID = UUID.fromString("bbbb2222-0000-0000-0000-000000000001");

	// User Credentials
	public static final String DEMO_PASSWORD = "P@ssw0rd123!";
	public static final String DOCTOR_EMAIL = "doctor@metro.org";
	public static final String CONSULTANT_EMAIL = "consultant@healthgroup.org";
	public static final String ADMIN_EMAIL = "admin@metro.org";
	public static final String NURSE_EMAIL = "nurse@metro.org";
	public static final String SUSPENDED_EMAIL = "suspended@metro.org";
	public static final String SURGICAL_GROUP_CODE = "SURGICAL_TEAM";

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final ScopeNodeRepository scopeNodeRepository;
	private final UserRoleAssignmentRepository userRoleAssignmentRepository;
	private final GroupRepository groupRepository;
	private final GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private final UserGroupMembershipRepository userGroupMembershipRepository;
	private final PasswordEncoderPort passwordEncoder;
	private final PatientRecordRepository patientRecordRepository;

	public PlaygroundDataSeeder(
			UserRepository userRepository,
			RoleRepository roleRepository,
			ScopeNodeRepository scopeNodeRepository,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			GroupRepository groupRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			PasswordEncoderPort passwordEncoder,
			PatientRecordRepository patientRecordRepository) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.scopeNodeRepository = scopeNodeRepository;
		this.userRoleAssignmentRepository = userRoleAssignmentRepository;
		this.groupRepository = groupRepository;
		this.groupRoleAssignmentRepository = groupRoleAssignmentRepository;
		this.userGroupMembershipRepository = userGroupMembershipRepository;
		this.passwordEncoder = passwordEncoder;
		this.patientRecordRepository = patientRecordRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void seed() {
		if (userRepository.findByEmail(DOCTOR_EMAIL).isPresent()) {
			log.info("Playground database already seeded. Skipping initial data population.");
			return;
		}

		log.info("Seeding ed-iam Playground demo dataset...");
		TenantId metroTenant = new TenantId(METRO_HOSPITAL_TENANT_ID);
		TenantId stJudeTenant = new TenantId(ST_JUDE_TENANT_ID);

		// 1. Seed Scope Tree for Metro General Hospital
		ScopeNode metroRoot = new ScopeNode(
				new ScopeNodeId(METRO_ROOT_SCOPE_ID),
				metroTenant,
				null,
				"METRO_HOSPITAL",
				"Metro General Hospital",
				"/" + METRO_HOSPITAL_TENANT_ID + "/" + METRO_ROOT_SCOPE_ID + "/",
				Instant.now(),
				Instant.now()
		);
		scopeNodeRepository.save(metroRoot);

		ScopeNode cardio = new ScopeNode(
				new ScopeNodeId(CARDIOLOGY_SCOPE_ID),
				metroTenant,
				metroRoot.getId(),
				"CARDIOLOGY",
				"Cardiology Department",
				metroRoot.getPath() + CARDIOLOGY_SCOPE_ID + "/",
				Instant.now(),
				Instant.now()
		);
		scopeNodeRepository.save(cardio);

		ScopeNode icu = new ScopeNode(
				new ScopeNodeId(ICU_SCOPE_ID),
				metroTenant,
				cardio.getId(),
				"ICU",
				"Cardiology Intensive Care Unit",
				cardio.getPath() + ICU_SCOPE_ID + "/",
				Instant.now(),
				Instant.now()
		);
		scopeNodeRepository.save(icu);

		ScopeNode pediatrics = new ScopeNode(
				new ScopeNodeId(PEDIATRICS_SCOPE_ID),
				metroTenant,
				metroRoot.getId(),
				"PEDIATRICS",
				"Pediatrics Department",
				metroRoot.getPath() + PEDIATRICS_SCOPE_ID + "/",
				Instant.now(),
				Instant.now()
		);
		scopeNodeRepository.save(pediatrics);

		// Seed Scope Tree for St. Jude
		ScopeNode stJudeRoot = new ScopeNode(
				new ScopeNodeId(ST_JUDE_ROOT_SCOPE_ID),
				stJudeTenant,
				null,
				"ST_JUDE",
				"St. Jude Medical Center",
				"/" + ST_JUDE_TENANT_ID + "/" + ST_JUDE_ROOT_SCOPE_ID + "/",
				Instant.now(),
				Instant.now()
		);
		scopeNodeRepository.save(stJudeRoot);

		// 2. Seed Roles
		Role clinicianRole = Role.createCustom(
				metroTenant,
				"CLINICIAN",
				"Clinician Staff",
				"Physicians with patient read/write access",
				Set.of("PATIENT_READ", "PATIENT_WRITE")
		);
		roleRepository.save(clinicianRole);

		Role consultantRole = Role.createCustom(
				metroTenant,
				"CONSULTANT",
				"Specialist Consultant",
				"Consultants with patient read and consultation privileges",
				Set.of("PATIENT_READ", "CLINICAL_CONSULT")
		);
		roleRepository.save(consultantRole);

		Role auditorRole = Role.createCustom(
				stJudeTenant,
				"AUDITOR",
				"Compliance Auditor",
				"External compliance auditing",
				Set.of("AUDIT_READ", "COMPLIANCE_VIEW")
		);
		roleRepository.save(auditorRole);

		Role adminRole = Role.createCustom(
				metroTenant,
				"HOSPITAL_ADMIN",
				"Hospital Administrator",
				"Tenant-wide hospital administrator",
				Set.of("*", "PATIENT_READ", "PATIENT_WRITE", "USER_ADMIN")
		);
		roleRepository.save(adminRole);

		// 3. Seed Users and Role Assignments
		String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

		// User A: Dr. Gregory House (Single-Tenant, Scoped to Cardiology)
		User doctor = User.create(DOCTOR_EMAIL, passwordHash, "Dr. Gregory House", false);
		userRepository.save(doctor);
		userRoleAssignmentRepository.save(
				UserRoleAssignment.create(doctor.getId(), clinicianRole.getId(), metroTenant, cardio.getId(), true)
		);

		// User B: Dr. Allison Cameron (Multi-Tenant across Metro & St. Jude)
		User consultant = User.create(CONSULTANT_EMAIL, passwordHash, "Dr. Allison Cameron", false);
		userRepository.save(consultant);
		userRoleAssignmentRepository.save(
				UserRoleAssignment.createTenantWide(consultant.getId(), consultantRole.getId(), metroTenant)
		);
		userRoleAssignmentRepository.save(
				UserRoleAssignment.createTenantWide(consultant.getId(), auditorRole.getId(), stJudeTenant)
		);

		// User C: Dr. Lisa Cuddy (Tenant Administrator in Metro)
		User admin = User.create(ADMIN_EMAIL, passwordHash, "Dr. Lisa Cuddy", false);
		userRepository.save(admin);
		userRoleAssignmentRepository.save(
				UserRoleAssignment.createTenantWide(admin.getId(), adminRole.getId(), metroTenant)
		);

		// 4. Seed Groups & Group Role Assignments
		Group surgicalGroup = Group.create(
				metroTenant,
				SURGICAL_GROUP_CODE,
				"Surgical Care Team",
				"Emergency surgical and acute patient care unit",
				"metro-surgeons"
		);
		groupRepository.save(surgicalGroup);

		// Assign CLINICIAN role to SURGICAL_TEAM scoped to Metro Root (inherits to all child departments)
		groupRoleAssignmentRepository.save(
				GroupRoleAssignment.create(
						surgicalGroup.getId(),
						clinicianRole.getId(),
						metroTenant,
						metroRoot.getId(),
						true
				)
		);

		// User D: Nurse Jackie Peyton (Group Role Inheritance: Member of SURGICAL_TEAM, no direct roles)
		User nurse = User.create(NURSE_EMAIL, passwordHash, "Nurse Jackie Peyton", false);
		userRepository.save(nurse);
		userGroupMembershipRepository.save(UserGroupMembership.of(surgicalGroup.getId(), nurse.getId()));

		// User E: Dr. Robert Chase (Suspended User Account Lifecycle Testing)
		User suspended = User.create(SUSPENDED_EMAIL, passwordHash, "Dr. Robert Chase (Suspended)", false);
		suspended.suspend();
		userRepository.save(suspended);
		userRoleAssignmentRepository.save(
				UserRoleAssignment.create(suspended.getId(), clinicianRole.getId(), metroTenant, cardio.getId(), true)
		);

		// 5. Seed Sample Patient Records
		patientRecordRepository.save(new PatientRecord(
				UUID.randomUUID(),
				METRO_HOSPITAL_TENANT_ID,
				CARDIOLOGY_SCOPE_ID,
				"Cardiology Department",
				"Johnathan Doe (45M)",
				"Severe Atrial Fibrillation",
				"Telemetry monitoring active. Scheduled for cardioversion.",
				Instant.now()
		));

		patientRecordRepository.save(new PatientRecord(
				UUID.randomUUID(),
				METRO_HOSPITAL_TENANT_ID,
				ICU_SCOPE_ID,
				"Cardiology ICU Ward",
				"Robert Chase (58M)",
				"Acute Myocardial Infarction",
				"Post-angioplasty recovery. Cardiac enzymes stabilizing.",
				Instant.now()
		));

		patientRecordRepository.save(new PatientRecord(
				UUID.randomUUID(),
				METRO_HOSPITAL_TENANT_ID,
				PEDIATRICS_SCOPE_ID,
				"Pediatrics Department",
				"Emily Chen (6F)",
				"Acute Bronchiolitis",
				"Supportive nebulizer treatment. SpO2 98% on room air.",
				Instant.now()
		));

		patientRecordRepository.save(new PatientRecord(
				UUID.randomUUID(),
				ST_JUDE_TENANT_ID,
				ST_JUDE_ROOT_SCOPE_ID,
				"St. Jude Oncology & Research",
				"Sarah Connor (42F)",
				"Clinical Trial Group B - Immunotherapy",
				"Enrolled in Protocol SJ-2026. Routine toxicity bloodwork within normal limits.",
				Instant.now()
		));

		log.info("=================================================================");
		log.info("  ed-iam Playground Ready! (Web UI: http://localhost:8080)");
		log.info("=================================================================");
		log.info("  Personas available (password: '{}'):", DEMO_PASSWORD);
		log.info("  1. Clinician (Cardiology Scope) : {}", DOCTOR_EMAIL);
		log.info("  2. Multi-Tenant Consultant      : {}", CONSULTANT_EMAIL);
		log.info("  3. Hospital Administrator       : {}", ADMIN_EMAIL);
		log.info("  4. Group Role Inheritor (Nurse) : {}", NURSE_EMAIL);
		log.info("  5. Suspended Account Staff      : {}", SUSPENDED_EMAIL);
		log.info("=================================================================");
	}
}
