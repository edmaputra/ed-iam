package io.github.edmaputra.iam.domain.context;

/**
 * Categorization of the entity or principal initiating an operation.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public enum ActorType {

	/**
	 * An interactive human user or administrator.
	 */
	USER,

	/**
	 * An internal system process, scheduled job, or administrative engine.
	 */
	SYSTEM,

	/**
	 * An automated machine, external integration, or machine-to-machine client credentials caller.
	 */
	MACHINE
}
