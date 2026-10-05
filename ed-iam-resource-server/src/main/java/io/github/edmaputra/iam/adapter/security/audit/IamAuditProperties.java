package io.github.edmaputra.iam.adapter.security.audit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for security auditing and domain event publication.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iam.audit")
public class IamAuditProperties {

	/**
	 * Whether security auditing is enabled. Defaults to true.
	 */
	private boolean enabled = true;

	/**
	 * Whether structured security audit event logging via SLF4J is enabled. Defaults to true.
	 */
	private boolean loggingEnabled = true;
}
