package com.careslot.appointment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentRulesTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void acceptsFutureAppointmentWithinMaximumDuration() {
        assertDoesNotThrow(() -> AppointmentRules.validateTimeRange(
            Instant.parse("2030-01-02T09:00:00Z"), Instant.parse("2030-01-02T10:00:00Z"), NOW));
    }

    @Test
    void rejectsNullPastInvertedAndOverlongAppointments() {
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.validateTimeRange(null, NOW.plusSeconds(60), NOW));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.validateTimeRange(NOW.minusSeconds(1), NOW.plusSeconds(60), NOW));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.validateTimeRange(NOW.plusSeconds(60), NOW.plusSeconds(30), NOW));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.validateTimeRange(NOW.plusSeconds(60), NOW.plusSeconds(60 + 121 * 60), NOW));
    }

    @Test
    void rejectsAppointmentsCrossingPracticeLocalMidnight() {
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.validateTimeRange(
            Instant.parse("2030-01-02T22:50:00Z"), Instant.parse("2030-01-02T23:10:00Z"), NOW));
    }

    @Test
    void requiresAnActiveAvailabilityWindow() {
        assertDoesNotThrow(() -> AppointmentRules.validateWithinAvailability(true));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.validateWithinAvailability(false));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.validateWithinAvailability(null));
    }
}
