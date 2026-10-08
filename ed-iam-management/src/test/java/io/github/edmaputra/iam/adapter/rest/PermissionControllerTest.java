package io.github.edmaputra.iam.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.PermissionManagementDtos.CreatePermissionRequest;
import io.github.edmaputra.iam.adapter.rest.dto.PermissionManagementDtos.PermissionResponse;
import io.github.edmaputra.iam.adapter.rest.dto.PermissionManagementDtos.UpdatePermissionRequest;
import io.github.edmaputra.iam.application.port.in.ManagePermissionUseCase;
import io.github.edmaputra.iam.application.port.in.PermissionCommands.CreatePermissionCommand;
import io.github.edmaputra.iam.application.port.in.PermissionCommands.UpdatePermissionCommand;
import io.github.edmaputra.iam.domain.model.Permission;
import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PermissionController}.
 *
 * @author edmaputra
 * @since 0.10.0
 */
class PermissionControllerTest {

	private ManagePermissionUseCase managePermissionUseCase;
	private PermissionController controller;

	@BeforeEach
	void setUp() {
		managePermissionUseCase = mock(ManagePermissionUseCase.class);
		controller = new PermissionController(managePermissionUseCase);
	}

	@Test
	@DisplayName("POST /api/v1/permissions creates and returns permission")
	void createPermissionReturnsCreated() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		Permission created = Permission.createCustom(tenantId, "ehr:view", "View EHR", "Desc", "EHR");

		when(managePermissionUseCase.createPermission(any(CreatePermissionCommand.class))).thenReturn(created);

		CreatePermissionRequest request = new CreatePermissionRequest(
				tenantUuid, "ehr:view", "View EHR", "Desc", "EHR");

		ResponseEntity<PermissionResponse> response = controller.createPermission(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("ehr:view");
		assertThat(response.getBody().name()).isEqualTo("View EHR");
		assertThat(response.getHeaders().getLocation()).isNotNull();
	}

	@Test
	@DisplayName("GET /api/v1/permissions/{id} returns matching permission")
	void getPermissionByIdReturnsOk() {
		Permission perm = Permission.createSystem("iam:perm:read", "Read Perm", null, "IAM");
		when(managePermissionUseCase.getPermissionById(perm.id())).thenReturn(perm);

		ResponseEntity<PermissionResponse> response = controller.getPermissionById(perm.id().value());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("iam:perm:read");
	}

	@Test
	@DisplayName("GET /api/v1/permissions/code/{code} returns matching permission")
	void getPermissionByCodeReturnsOk() {
		Permission perm = Permission.createSystem("iam:perm:read", "Read Perm", null, "IAM");
		when(managePermissionUseCase.getPermissionByCode(null, "iam:perm:read")).thenReturn(perm);

		ResponseEntity<PermissionResponse> response = controller.getPermissionByCode("iam:perm:read", null, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("iam:perm:read");
	}

	@Test
	@DisplayName("GET /api/v1/permissions lists permissions")
	void getPermissionsReturnsList() {
		Permission perm = Permission.createSystem("sys:1", "System 1", null, "CAT");
		when(managePermissionUseCase.getPermissions(null, "CAT")).thenReturn(List.of(perm));

		ResponseEntity<List<PermissionResponse>> response = controller.getPermissions(null, null, "CAT");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).hasSize(1);
		assertThat(response.getBody().get(0).code()).isEqualTo("sys:1");
	}

	@Test
	@DisplayName("PUT /api/v1/permissions/{id} updates and returns permission")
	void updatePermissionReturnsOk() {
		TenantId tenantId = TenantId.generate();
		Permission updated = Permission.createCustom(tenantId, "ehr:edit", "New Name", "New Desc", "NEW_CAT");
		when(managePermissionUseCase.updatePermission(any(UpdatePermissionCommand.class))).thenReturn(updated);

		UpdatePermissionRequest request = new UpdatePermissionRequest("New Name", "New Desc", "NEW_CAT");

		ResponseEntity<PermissionResponse> response = controller.updatePermission(updated.id().value(), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().name()).isEqualTo("New Name");
	}

	@Test
	@DisplayName("DELETE /api/v1/permissions/{id} deletes permission")
	void deletePermissionReturnsNoContent() {
		UUID id = UUID.randomUUID();

		ResponseEntity<Void> response = controller.deletePermission(id);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(managePermissionUseCase).deletePermission(new PermissionId(id));
	}
}
