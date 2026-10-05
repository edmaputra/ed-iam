package io.github.edmaputra.iam.adapter.security.telemetry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OpenTelemetry metrics, tracing, and observability.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iam.telemetry")
public class IamTelemetryProperties {

	/**
	 * Whether telemetry instrumentation is enabled. Defaults to true.
	 */
	private boolean enabled = true;

	/**
	 * Whether native Micrometer metrics are enabled. Defaults to true.
	 */
	private boolean metricsEnabled = true;

	/**
	 * Whether OpenTelemetry distributed tracing spans are enabled. Defaults to true.
	 */
	private boolean tracingEnabled = true;
}
