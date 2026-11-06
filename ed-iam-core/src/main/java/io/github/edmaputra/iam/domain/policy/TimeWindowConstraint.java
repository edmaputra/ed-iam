package io.github.edmaputra.iam.domain.policy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Contextual constraint verifying that the request occurs within designated time-of-day windows,
 * days-of-week, and specific timezones (e.g. clinic business hours).
 *
 * @param startTime optional start of permitted time window (inclusive)
 * @param endTime optional end of permitted time window (inclusive)
 * @param daysOfWeek optional set of permitted days of the week (empty means all days)
 * @param timeZone timezone identifier (e.g., "America/New_York", "UTC", "Asia/Jakarta"); defaults to UTC
 * @author edmaputra
 * @since 0.14.0
 */
public record TimeWindowConstraint(
		LocalTime startTime,
		LocalTime endTime,
		Set<DayOfWeek> daysOfWeek,
		String timeZone) implements PolicyConstraint {

	public static final String TYPE = "TIME_WINDOW";

	public TimeWindowConstraint {
		daysOfWeek = daysOfWeek == null ? Set.of() : Set.copyOf(daysOfWeek);
		timeZone = (timeZone == null || timeZone.isBlank()) ? "UTC" : timeZone.trim();
	}

	public TimeWindowConstraint(LocalTime startTime, LocalTime endTime, Set<DayOfWeek> daysOfWeek, ZoneId zoneId) {
		this(startTime, endTime, daysOfWeek, zoneId != null ? zoneId.getId() : "UTC");
	}

	public ZoneId zoneId() {
		try {
			return ZoneId.of(timeZone);
		} catch (Exception e) {
			return ZoneOffset.UTC;
		}
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public boolean evaluate(PolicyEvaluationContext context) {
		Instant timestamp = context.environment().timestamp();
		if (timestamp == null) {
			timestamp = Instant.now();
		}

		ZoneId zoneId;
		try {
			zoneId = ZoneId.of(timeZone);
		} catch (Exception e) {
			zoneId = ZoneOffset.UTC;
		}

		ZonedDateTime zdt = timestamp.atZone(zoneId);

		if (!daysOfWeek.isEmpty() && !daysOfWeek.contains(zdt.getDayOfWeek())) {
			return false;
		}

		LocalTime currentTime = zdt.toLocalTime();

		if (startTime != null && endTime != null) {
			if (startTime.isBefore(endTime)) {
				// Normal intra-day window (e.g. 08:00 to 18:00)
				if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
					return false;
				}
			} else if (startTime.isAfter(endTime)) {
				// Overnight window spanning midnight (e.g. 22:00 to 06:00)
				if (currentTime.isBefore(startTime) && currentTime.isAfter(endTime)) {
					return false;
				}
			} else {
				// Exact match
				if (!currentTime.equals(startTime)) {
					return false;
				}
			}
		} else if (startTime != null && currentTime.isBefore(startTime)) {
			return false;
		} else if (endTime != null && currentTime.isAfter(endTime)) {
			return false;
		}

		return true;
	}

	public static TimeWindowConstraint businessHours(LocalTime start, LocalTime end, String timeZone) {
		return new TimeWindowConstraint(start, end, Set.of(
				DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
		), timeZone);
	}
}
