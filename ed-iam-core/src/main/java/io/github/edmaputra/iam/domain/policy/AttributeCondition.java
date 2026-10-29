package io.github.edmaputra.iam.domain.policy;

import java.util.Collection;
import java.util.Objects;

/**
 * Attribute-based constraint evaluating a subject, resource, or environment attribute against
 * a fixed expected value or another entity's attribute.
 *
 * @param source entity source of the attribute being inspected
 * @param attributeName name of the attribute
 * @param operator comparison operator
 * @param expectedValue fixed expected value (if not comparing against another entity attribute)
 * @param comparisonSource optional entity source to compare against (e.g. comparing subject to resource)
 * @param comparisonAttributeName optional attribute name on the comparison entity
 * @author edmaputra
 * @since 0.14.0
 */
public record AttributeCondition(
		AttributeSource source,
		String attributeName,
		AttributeOperator operator,
		Object expectedValue,
		AttributeSource comparisonSource,
		String comparisonAttributeName) implements PolicyConstraint {

	public static final String TYPE = "ATTRIBUTE";

	public AttributeCondition {
		source = source != null ? source : AttributeSource.SUBJECT;
		Objects.requireNonNull(attributeName, "Attribute name must not be null.");
		if (attributeName.isBlank()) {
			throw new IllegalArgumentException("Attribute name must not be blank.");
		}
		attributeName = attributeName.trim();
		operator = operator != null ? operator : AttributeOperator.EQUALS;
		comparisonAttributeName = comparisonAttributeName != null ? comparisonAttributeName.trim() : null;
	}

	public AttributeCondition(AttributeSource source, String attributeName, AttributeOperator operator, Object expectedValue) {
		this(source, attributeName, operator, expectedValue, null, null);
	}

	public Object targetValue() {
		return expectedValue;
	}

	public AttributeSource targetSource() {
		return comparisonSource;
	}

	public String targetAttributeName() {
		return comparisonAttributeName;
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public boolean evaluate(PolicyEvaluationContext context) {
		Object actualValue = extractAttribute(source, attributeName, context);

		if (operator == AttributeOperator.EXISTS) {
			return actualValue != null;
		}

		Object targetVal = comparisonSource != null && comparisonAttributeName != null
				? extractAttribute(comparisonSource, comparisonAttributeName, context)
				: expectedValue;

		if (actualValue == null && targetVal == null) {
			return operator == AttributeOperator.EQUALS;
		}
		if (actualValue == null || targetVal == null) {
			return operator == AttributeOperator.NOT_EQUALS;
		}

		return switch (operator) {
			case EQUALS -> compareEquality(actualValue, targetVal);
			case NOT_EQUALS -> !compareEquality(actualValue, targetVal);
			case CONTAINS -> evaluateContains(actualValue, targetVal);
			case IN -> evaluateIn(actualValue, targetVal);
			case GREATER_THAN -> compareOrdered(actualValue, targetVal) > 0;
			case GREATER_THAN_OR_EQUAL -> compareOrdered(actualValue, targetVal) >= 0;
			case LESS_THAN -> compareOrdered(actualValue, targetVal) < 0;
			case LESS_THAN_OR_EQUAL -> compareOrdered(actualValue, targetVal) <= 0;
			case EXISTS -> true;
		};
	}

	private static Object extractAttribute(AttributeSource src, String attr, PolicyEvaluationContext ctx) {
		if (src == null || attr == null || ctx == null) {
			return null;
		}
		return switch (src) {
			case SUBJECT -> {
				var s = ctx.subject();
				yield switch (attr) {
					case "userId" -> s.userId() != null ? s.userId().toString() : null;
					case "tenantId" -> s.tenantId() != null ? s.tenantId().toString() : null;
					case "email" -> s.email();
					case "roles" -> s.roles();
					case "groups" -> s.groups();
					case "permissions" -> s.permissions();
					default -> s.attributes().get(attr);
				};
			}
			case RESOURCE -> {
				var r = ctx.resource();
				yield switch (attr) {
					case "id" -> r.id();
					case "type" -> r.type();
					case "tenantId" -> r.tenantId() != null ? r.tenantId().toString() : null;
					case "ownerId" -> r.ownerId() != null ? r.ownerId().toString() : null;
					default -> r.attributes().get(attr);
				};
			}
			case ENVIRONMENT -> {
				var e = ctx.environment();
				yield switch (attr) {
					case "clientIp" -> e.clientIp();
					case "userAgent" -> e.userAgent();
					case "managed" -> e.devicePosture().managed();
					case "trustLevel" -> e.devicePosture().trustLevel();
					case "platform" -> e.devicePosture().platform();
					default -> e.attributes().get(attr);
				};
			}
		};
	}

	private static boolean compareEquality(Object a, Object b) {
		if (Objects.equals(a, b)) {
			return true;
		}
		return String.valueOf(a).equalsIgnoreCase(String.valueOf(b));
	}

	private static boolean evaluateContains(Object haystack, Object needle) {
		if (haystack instanceof Collection<?> coll) {
			for (Object elem : coll) {
				if (compareEquality(elem, needle)) {
					return true;
				}
			}
			return false;
		}
		return String.valueOf(haystack).contains(String.valueOf(needle));
	}

	private static boolean evaluateIn(Object item, Object container) {
		if (container instanceof Collection<?> coll) {
			for (Object elem : coll) {
				if (compareEquality(item, elem)) {
					return true;
				}
			}
			return false;
		}
		return compareEquality(item, container);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static int compareOrdered(Object a, Object b) {
		if (a instanceof Number na && b instanceof Number nb) {
			return Double.compare(na.doubleValue(), nb.doubleValue());
		}
		try {
			double da = Double.parseDouble(String.valueOf(a));
			double db = Double.parseDouble(String.valueOf(b));
			return Double.compare(da, db);
		} catch (NumberFormatException ignored) {
			// Fallback to string comparison
		}
		if (a instanceof Comparable ca && b != null) {
			try {
				return ca.compareTo(b);
			} catch (Exception ignored) {
			}
		}
		return String.valueOf(a).compareTo(String.valueOf(b));
	}
}
