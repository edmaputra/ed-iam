package io.github.edmaputra.iam.it.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Full Spring Boot sample application used as a host harness to integration test ed-iam.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootApplication
public class TestIamApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestIamApplication.class, args);
	}
}
