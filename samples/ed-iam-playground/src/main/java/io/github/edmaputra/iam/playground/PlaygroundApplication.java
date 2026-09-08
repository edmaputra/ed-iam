package io.github.edmaputra.iam.playground;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application runner for the ed-iam Interactive Playground.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "io.github.edmaputra.iam.playground")
@EntityScan(basePackages = "io.github.edmaputra.iam.playground")
public class PlaygroundApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlaygroundApplication.class, args);
	}
}
