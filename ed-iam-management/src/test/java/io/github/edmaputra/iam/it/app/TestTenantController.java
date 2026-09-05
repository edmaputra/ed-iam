package io.github.edmaputra.iam.it.app;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test REST controller verifying that the host tenant context was bridged during request execution.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/test/tenant")
public class TestTenantController {

	@GetMapping
	public ResponseEntity<Map<String, Object>> getCurrentHostTenant() {
		UUID tenantId = TestTenantContextHolder.getTenantId();
		return ResponseEntity.ok(Map.of(
				"active", tenantId != null,
				"tenantId", tenantId != null ? tenantId.toString() : ""
		));
	}
}
