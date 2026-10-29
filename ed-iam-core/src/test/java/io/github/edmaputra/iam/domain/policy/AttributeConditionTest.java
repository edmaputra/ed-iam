package io.github.edmaputra.iam.domain.policy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AttributeCondition Unit Tests")
class AttributeConditionTest {

	@Test
	@DisplayName("Should evaluate attribute equality and in-collection matching")
	void shouldEvaluateEqualityAndIn() {
		PolicyEvaluationContext context = new PolicyEvaluationContext(
				new SubjectAttributes(UUID.randomUUID(), UUID.randomUUID(), "alice@hospital.org", Set.of("DOCTOR"), Set.of(), Set.of(),
						Map.of("department", "Cardiology", "clearanceLevel", 3)),
				"read",
				new ResourceAttributes("rec-1", "patient-record", UUID.randomUUID(), UUID.randomUUID(),
						Map.of("department", "Cardiology", "confidentialityLevel", 2)),
				new EnvironmentAttributes("127.0.0.1", null, "agent", DevicePosture.unknown(), Map.of())
		);

		// Subject attribute == fixed value
		AttributeCondition deptCondition = new AttributeCondition(
				AttributeSource.SUBJECT, "department", AttributeOperator.EQUALS, "Cardiology"
		);
		assertThat(deptCondition.evaluate(context)).isTrue();

		// Subject clearance >= resource confidentiality
		AttributeCondition clearanceCond = new AttributeCondition(
				AttributeSource.SUBJECT, "clearanceLevel", AttributeOperator.GREATER_THAN_OR_EQUAL, null,
				AttributeSource.RESOURCE, "confidentialityLevel"
		);
		assertThat(clearanceCond.evaluate(context)).isTrue();

		// Cross-entity department match (subject.department == resource.department)
		AttributeCondition sameDeptCond = new AttributeCondition(
				AttributeSource.SUBJECT, "department", AttributeOperator.EQUALS, null,
				AttributeSource.RESOURCE, "department"
		);
		assertThat(sameDeptCond.evaluate(context)).isTrue();

		// IN operator
		AttributeCondition inDepts = new AttributeCondition(
				AttributeSource.SUBJECT, "department", AttributeOperator.IN, List.of("Cardiology", "Neurology")
		);
		assertThat(inDepts.evaluate(context)).isTrue();

		// EXISTS operator
		AttributeCondition existsCond = new AttributeCondition(
				AttributeSource.SUBJECT, "department", AttributeOperator.EXISTS, null
		);
		assertThat(existsCond.evaluate(context)).isTrue();
	}
}
