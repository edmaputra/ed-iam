package io.github.edmaputra.iam.domain.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative security annotation defining permission requirements on REST endpoints or controllers.
 *
 * <p>When present on a method or type, requests must be authenticated and the current actor
 * must possess the specified permissions according to the configured {@link #logical()} operator.
 * Platform superadmins are granted access unconditionally.
 *
 * @author edmaputra
 * @since 0.1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

	/**
	 * Required permission string(s) (e.g. {@code "iam:user:create"}).
	 *
	 * @return array of required permission identifiers
	 */
	String[] value();

	/**
	 * Logical condition to evaluate when multiple permissions are specified.
	 * Defaults to {@link Logical#AND}.
	 *
	 * @return logical operator
	 */
	Logical logical() default Logical.AND;
}
