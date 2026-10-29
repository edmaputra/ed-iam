package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.edmaputra.iam.domain.policy.AttributeCondition;
import io.github.edmaputra.iam.domain.policy.AttributeOperator;
import io.github.edmaputra.iam.domain.policy.AttributeSource;
import io.github.edmaputra.iam.domain.policy.DevicePostureConstraint;
import io.github.edmaputra.iam.domain.policy.NetworkCidrConstraint;
import io.github.edmaputra.iam.domain.policy.PolicyConstraint;
import io.github.edmaputra.iam.domain.policy.TimeWindowConstraint;

/**
 * Serializes and deserializes {@link PolicyConstraint} collections to and from JSON.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public class PolicyConstraintJsonConverter {

	private static final Logger log = LoggerFactory.getLogger(PolicyConstraintJsonConverter.class);

	private static final String TYPE_KEY = "type";
	private static final String TYPE_TIME_WINDOW = "TIME_WINDOW";
	private static final String TYPE_NETWORK_CIDR = "NETWORK_CIDR";
	private static final String TYPE_DEVICE_POSTURE = "DEVICE_POSTURE";
	private static final String TYPE_ATTRIBUTE_CONDITION = "ATTRIBUTE_CONDITION";

	private final ObjectMapper objectMapper;

	public PolicyConstraintJsonConverter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
	}

	public String toJson(List<PolicyConstraint> constraints) {
		if (constraints == null || constraints.isEmpty()) {
			return "[]";
		}

		List<Map<String, Object>> mapped = new ArrayList<>();
		for (PolicyConstraint constraint : constraints) {
			if (constraint instanceof TimeWindowConstraint tw) {
				Map<String, Object> map = new LinkedHashMap<>();
				map.put(TYPE_KEY, TYPE_TIME_WINDOW);
				map.put("startTime", tw.startTime() != null ? tw.startTime().toString() : null);
				map.put("endTime", tw.endTime() != null ? tw.endTime().toString() : null);
				map.put("daysOfWeek", tw.daysOfWeek() != null ? tw.daysOfWeek().stream().map(Enum::name).toList() : List.of());
				map.put("zoneId", tw.zoneId() != null ? tw.zoneId().getId() : null);
				mapped.add(map);
			} else if (constraint instanceof NetworkCidrConstraint nc) {
				Map<String, Object> map = new LinkedHashMap<>();
				map.put(TYPE_KEY, TYPE_NETWORK_CIDR);
				map.put("allowedCidrs", nc.allowedCidrs());
				map.put("deniedCidrs", nc.deniedCidrs());
				mapped.add(map);
			} else if (constraint instanceof DevicePostureConstraint dp) {
				Map<String, Object> map = new LinkedHashMap<>();
				map.put(TYPE_KEY, TYPE_DEVICE_POSTURE);
				map.put("requireManaged", dp.requireManaged());
				map.put("minTrustLevel", dp.minTrustLevel());
				map.put("allowedTrustLevels", dp.allowedTrustLevels());
				map.put("allowedPlatforms", dp.allowedPlatforms());
				mapped.add(map);
			} else if (constraint instanceof AttributeCondition ac) {
				Map<String, Object> map = new LinkedHashMap<>();
				map.put(TYPE_KEY, TYPE_ATTRIBUTE_CONDITION);
				map.put("source", ac.source() != null ? ac.source().name() : null);
				map.put("attributeName", ac.attributeName());
				map.put("operator", ac.operator() != null ? ac.operator().name() : null);
				map.put("targetValue", ac.targetValue());
				map.put("targetSource", ac.targetSource() != null ? ac.targetSource().name() : null);
				map.put("targetAttributeName", ac.targetAttributeName());
				mapped.add(map);
			} else {
				log.warn("Unknown PolicyConstraint type [{}], skipping serialization.", constraint.getClass().getName());
			}
		}

		try {
			return objectMapper.writeValueAsString(mapped);
		} catch (Exception ex) {
			log.error("Failed to serialize policy constraints to JSON", ex);
			throw new IllegalStateException("Failed to serialize policy constraints to JSON", ex);
		}
	}

	public List<PolicyConstraint> fromJson(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}

		try {
			List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<>() {});
			List<PolicyConstraint> constraints = new ArrayList<>();

			for (Map<String, Object> map : rawList) {
				String type = (String) map.get(TYPE_KEY);
				if (type == null) {
					continue;
				}

				switch (type) {
					case TYPE_TIME_WINDOW -> {
						LocalTime startTime = map.get("startTime") != null ? LocalTime.parse((String) map.get("startTime")) : null;
						LocalTime endTime = map.get("endTime") != null ? LocalTime.parse((String) map.get("endTime")) : null;
						Set<DayOfWeek> daysOfWeek = map.get("daysOfWeek") instanceof Collection<?> col
								? col.stream().map(Object::toString).map(DayOfWeek::valueOf).collect(Collectors.toSet())
								: Set.of();
						ZoneId zoneId = map.get("zoneId") != null ? ZoneId.of((String) map.get("zoneId")) : null;
						constraints.add(new TimeWindowConstraint(startTime, endTime, daysOfWeek, zoneId));
					}
					case TYPE_NETWORK_CIDR -> {
						Set<String> allowedCidrs = map.get("allowedCidrs") instanceof Collection<?> col
								? col.stream().map(Object::toString).collect(Collectors.toSet())
								: Set.of();
						Set<String> deniedCidrs = map.get("deniedCidrs") instanceof Collection<?> col
								? col.stream().map(Object::toString).collect(Collectors.toSet())
								: Set.of();
						constraints.add(new NetworkCidrConstraint(allowedCidrs, deniedCidrs));
					}
					case TYPE_DEVICE_POSTURE -> {
						Boolean requireManaged = (Boolean) map.get("requireManaged");
						String minTrustLevel = (String) map.get("minTrustLevel");
						if (minTrustLevel == null && map.get("allowedTrustLevels") instanceof Collection<?> col && !col.isEmpty()) {
							minTrustLevel = col.iterator().next().toString();
						}
						Set<String> allowedPlatforms = map.get("allowedPlatforms") instanceof Collection<?> col
								? col.stream().map(Object::toString).collect(Collectors.toSet())
								: Set.of();
						constraints.add(new DevicePostureConstraint(requireManaged, minTrustLevel, allowedPlatforms));
					}
					case TYPE_ATTRIBUTE_CONDITION -> {
						AttributeSource source = map.get("source") != null ? AttributeSource.valueOf((String) map.get("source")) : AttributeSource.SUBJECT;
						String attributeName = (String) map.get("attributeName");
						AttributeOperator operator = map.get("operator") != null ? AttributeOperator.valueOf((String) map.get("operator")) : AttributeOperator.EQUALS;
						Object targetValue = map.get("targetValue");
						AttributeSource targetSource = map.get("targetSource") != null ? AttributeSource.valueOf((String) map.get("targetSource")) : null;
						String targetAttributeName = (String) map.get("targetAttributeName");
						constraints.add(new AttributeCondition(source, attributeName, operator, targetValue, targetSource, targetAttributeName));
					}
					default -> log.warn("Encountered unknown policy constraint type in JSON: [{}]", type);
				}
			}

			return List.copyOf(constraints);
		} catch (Exception ex) {
			log.error("Failed to deserialize policy constraints from JSON: {}", json, ex);
			return List.of();
		}
	}
}
