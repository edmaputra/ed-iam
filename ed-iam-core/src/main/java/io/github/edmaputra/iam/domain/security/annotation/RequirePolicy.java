package io.github.edmaputra.iam.domain.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative security annotation enforcing dynamic Attribute-Based Access Control (ABAC) policies.
 *
 * <p>When present on a controller method or class, requests are evaluated against active policies
 * using the configured action, resource type, and contextual attributes (client IP, time-of-day,
 * device posture, actor attributes).
 *
 * @author edmaputra
 * @since 0.14.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePolicy {

	/**
	 * Requested action or operation code (e.g., "read", "update", "patient:read").
	 *
	 * @return action string
	 */
	String action();

	/**
	 * Target resource type or category (e.g., "patient-record", "clinical-document", "user").
	 *
	 * @return resource type string
	 */
	String resourceType();

	/**
	 * Optional specific policy name to require (if specified, this specific policy must evaluate to PERMIT).
	 *
	 * @return policy name, or empty string if evaluating all matching policies
	 */
	String policyName() default "";
}
