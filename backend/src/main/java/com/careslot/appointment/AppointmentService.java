package com.careslot.appointment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final JdbcTemplate jdbc;
    public AppointmentService(AppointmentRepository appointments, PatientRepository patients, JdbcTemplate jdbc) { this.appointments = appointments; this.patients = patients; this.jdbc = jdbc; }

    @Transactional
    public Appointment book(UUID practitionerId, UUID patientId, Instant startsAt, Instant endsAt, String reasonCode) {
        return book(practitionerId, patientId, startsAt, endsAt, reasonCode, "Consultation");
    }
    @Transactional
    public Appointment book(UUID practitionerId, UUID patientId, Instant startsAt, Instant endsAt, String reasonCode, String visitType) {
        AppointmentRules.validateTimeRange(startsAt, endsAt, Instant.now());
        LocalDateTime localStart = LocalDateTime.ofInstant(startsAt, java.time.ZoneId.of("Europe/Paris"));
        LocalDateTime localEnd = LocalDateTime.ofInstant(endsAt, java.time.ZoneId.of("Europe/Paris"));
        // Serialize booking decisions for this practitioner's local day. The database
        // exclusion constraint remains the final guard against overlaps.
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            (PreparedStatementCallback<Void>) ps -> {
                ps.setString(1, practitionerId + ":" + localStart.toLocalDate());
                ps.execute();
                return null;
            });
        Boolean withinHours = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM practitioner_availability
              WHERE practitioner_id = ? AND day_of_week = ? AND active
                AND starts_at <= ? AND ends_at >= ?)
            """, Boolean.class, practitionerId, localStart.getDayOfWeek().getValue(), localStart.toLocalTime(), localEnd.toLocalTime());
        AppointmentRules.validateWithinAvailability(withinHours);
        try {
            return appointments.saveAndFlush(new Appointment(practitionerId, patientId, startsAt, endsAt, reasonCode, visitType));
        } catch (DataIntegrityViolationException conflict) {
            throw new AppointmentConflictException();
        }
    }
    @Transactional
    public Appointment bookForSubject(UUID practitionerId, String subject, String displayName, Instant startsAt, Instant endsAt, String reasonCode, String visitType, String requestId) {
        UUID patientId = UUID.nameUUIDFromBytes(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        patients.findById(patientId).orElseGet(() -> patients.save(new Patient(subject, displayName)));
        Appointment created = book(practitionerId, patientId, startsAt, endsAt, reasonCode, visitType);
        jdbc.update("INSERT INTO audit_events(actor_subject, action, resource_type, resource_id, request_id) VALUES (?, ?, ?, ?, ?)",
            subject, "APPOINTMENT_BOOKED", "appointment", created.getId(), requestId);
        return created;
    }
    @Transactional(readOnly=true)
    public Appointment get(UUID id) { return appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException()); }
}
