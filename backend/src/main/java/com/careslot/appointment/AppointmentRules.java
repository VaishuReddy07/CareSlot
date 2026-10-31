package com.careslot.appointment;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

final class AppointmentRules {
    private static final ZoneId PRACTICE_ZONE = ZoneId.of("Europe/Paris");

    private AppointmentRules() {}

    static void validateTimeRange(Instant startsAt, Instant endsAt, Instant now) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt) || startsAt.isBefore(now)) {
            throw new IllegalArgumentException("Choose a valid future appointment time.");
        }
        if (Duration.between(startsAt, endsAt).toMinutes() > 120) {
            throw new IllegalArgumentException("Appointment duration cannot exceed 120 minutes.");
        }
        LocalDateTime localStart = LocalDateTime.ofInstant(startsAt, PRACTICE_ZONE);
        LocalDateTime localEnd = LocalDateTime.ofInstant(endsAt, PRACTICE_ZONE);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            throw new IllegalArgumentException("An appointment must fit within one day.");
        }
    }

    static void validateWithinAvailability(Boolean withinHours) {
        if (!Boolean.TRUE.equals(withinHours)) {
            throw new IllegalArgumentException("That time is outside the practitioner’s availability.");
        }
    }
}

