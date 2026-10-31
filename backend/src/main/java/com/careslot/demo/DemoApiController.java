package com.careslot.demo;

import com.careslot.appointment.Appointment;
import com.careslot.appointment.AppointmentConflictException;
import com.careslot.appointment.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnExpression("'${careslot.demo-mode:false}' == 'true'")
public class DemoApiController {
    private static final UUID PRACTITIONER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final AppointmentService appointments;
    private final JdbcTemplate jdbc;

    public DemoApiController(AppointmentService appointments, JdbcTemplate jdbc) {
        this.appointments = appointments;
        this.jdbc = jdbc;
    }

    public record BookingRequest(@NotBlank @Size(max=120) String patientName,
                                 @NotBlank @Size(max=48) String visitType,
                                 @NotNull @Future Instant startsAt,
                                 @NotNull @Future Instant endsAt) {}
    public record AppointmentItem(UUID id, String time, String name, String kind, String status, String initials, String color) {}
    public record PatientItem(UUID id, String name, long appointments) {}
    public record AvailabilitySlot(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
    public record AvailabilityRequest(@NotNull List<@Valid AvailabilitySlot> slots) {}
    public record NoteRequest(@NotBlank @Size(max=6000) String draftText) {}
    public record NoteItem(UUID appointmentId, String patientName, String draftText, boolean aiGenerated) {}

    @GetMapping("/appointments")
    public List<AppointmentItem> appointments() {
        return jdbc.query("""
            SELECT a.id, to_char(a.starts_at AT TIME ZONE 'Europe/Paris', 'HH24:MI') AS local_time,
                   p.display_name, a.visit_type, a.status
            FROM appointments a JOIN patients p ON p.id = a.patient_id
            WHERE a.practitioner_id = ? AND a.status = 'BOOKED'
            ORDER BY a.starts_at
            """, (rs, row) -> {
                String name = rs.getString("display_name");
                String initials = java.util.Arrays.stream(name.trim().split("\\s+"))
                    .filter(part -> !part.isBlank()).map(part -> part.substring(0, 1))
                    .limit(2).reduce("", String::concat).toUpperCase(java.util.Locale.ROOT);
                String[] colors = {"peach", "blue", "lavender", "mint", "sand"};
                return new AppointmentItem(rs.getObject("id", UUID.class), rs.getString("local_time"), name,
                    rs.getString("visit_type") + " · Cardiology", "Confirmed", initials, colors[row % colors.length]);
            }, PRACTITIONER_ID);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentItem book(@Valid @RequestBody BookingRequest request) {
        String subject = "demo:" + request.patientName().trim().toLowerCase(java.util.Locale.ROOT);
        Appointment appointment = appointments.bookForSubject(PRACTITIONER_ID, subject, request.patientName().trim(),
            request.startsAt(), request.endsAt(), null, request.visitType().trim(), UUID.randomUUID().toString());
        String localTime = jdbc.queryForObject("SELECT to_char(?::timestamptz AT TIME ZONE 'Europe/Paris', 'HH24:MI')", String.class,
            java.sql.Timestamp.from(appointment.getStartsAt()));
        String initials = java.util.Arrays.stream(request.patientName().trim().split("\\s+"))
            .filter(part -> !part.isBlank()).map(part -> part.substring(0, 1)).limit(2)
            .reduce("", String::concat).toUpperCase(java.util.Locale.ROOT);
        return new AppointmentItem(appointment.getId(), localTime, request.patientName().trim(),
            appointment.getVisitType() + " · Cardiology", appointment.getStatus(), initials, "mint");
    }

    @GetMapping("/patients")
    public List<PatientItem> patients() {
        return jdbc.query("""
            SELECT p.id, p.display_name, count(a.id) AS appointment_count
            FROM patients p JOIN appointments a ON a.patient_id = p.id
            WHERE a.practitioner_id = ?
            GROUP BY p.id, p.display_name ORDER BY p.display_name
            """, (rs, row) -> new PatientItem(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getLong("appointment_count")), PRACTITIONER_ID);
    }

    @GetMapping("/availability")
    public List<AvailabilitySlot> availability() {
        return jdbc.query("SELECT day_of_week, starts_at, ends_at FROM practitioner_availability WHERE practitioner_id = ? AND active ORDER BY day_of_week, starts_at",
            (rs, row) -> new AvailabilitySlot(rs.getInt("day_of_week"), rs.getObject("starts_at", LocalTime.class), rs.getObject("ends_at", LocalTime.class)), PRACTITIONER_ID);
    }

    @PutMapping("/availability")
    @Transactional
    public List<AvailabilitySlot> saveAvailability(@Valid @RequestBody AvailabilityRequest request) {
        java.util.Map<Integer, List<AvailabilitySlot>> byDay = request.slots().stream().collect(java.util.stream.Collectors.groupingBy(AvailabilitySlot::dayOfWeek));
        for (List<AvailabilitySlot> day : byDay.values()) {
            List<AvailabilitySlot> sorted = day.stream().sorted(java.util.Comparator.comparing(AvailabilitySlot::startsAt)).toList();
            for (int i = 0; i < sorted.size(); i++) {
                AvailabilitySlot slot = sorted.get(i);
                if (!slot.endsAt().isAfter(slot.startsAt())) throw new IllegalArgumentException("Availability end time must be after start time.");
                if (i > 0 && sorted.get(i - 1).endsAt().isAfter(slot.startsAt())) throw new IllegalArgumentException("Availability slots cannot overlap.");
            }
        }
        jdbc.update("DELETE FROM practitioner_availability WHERE practitioner_id = ?", PRACTITIONER_ID);
        jdbc.batchUpdate("INSERT INTO practitioner_availability(practitioner_id, day_of_week, starts_at, ends_at) VALUES (?, ?, ?, ?)",
            request.slots(), 100, (ps, slot) -> {
                ps.setObject(1, PRACTITIONER_ID); ps.setInt(2, slot.dayOfWeek());
                ps.setObject(3, slot.startsAt()); ps.setObject(4, slot.endsAt());
            });
        jdbc.update("INSERT INTO audit_events(actor_subject, action, resource_type, resource_id) VALUES (?, ?, ?, ?)",
            "demo:practitioner", "AVAILABILITY_UPDATED", "practitioner", PRACTITIONER_ID);
        return availability();
    }

    @GetMapping("/notes")
    public List<NoteItem> notes() {
        return jdbc.query("""
            SELECT v.appointment_id, p.display_name, v.draft_text, v.ai_generated
            FROM visit_notes v JOIN appointments a ON a.id = v.appointment_id
            JOIN patients p ON p.id = a.patient_id
            WHERE a.practitioner_id = ? ORDER BY v.created_at DESC
            """, (rs, row) -> new NoteItem(rs.getObject("appointment_id", UUID.class), rs.getString("display_name"), rs.getString("draft_text"), rs.getBoolean("ai_generated")), PRACTITIONER_ID);
    }

    @PutMapping("/notes/{appointmentId}")
    @Transactional
    public NoteItem saveNote(@PathVariable UUID appointmentId, @Valid @RequestBody NoteRequest request) {
        Appointment appointment = appointments.get(appointmentId);
        if (!PRACTITIONER_ID.equals(appointment.getPractitionerId())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.update("""
            INSERT INTO visit_notes(appointment_id, practitioner_subject, draft_text, ai_generated)
            VALUES (?, ?, ?, false)
            ON CONFLICT (appointment_id) DO UPDATE SET draft_text = EXCLUDED.draft_text,
              ai_generated = false, reviewed_at = NULL, version = visit_notes.version + 1
            """, appointmentId, "demo:practitioner", request.draftText().trim());
        jdbc.update("INSERT INTO audit_events(actor_subject, action, resource_type, resource_id) VALUES (?, ?, ?, ?)",
            "demo:practitioner", "VISIT_NOTE_SAVED", "appointment", appointmentId);
        return jdbc.queryForObject("""
            SELECT v.appointment_id, p.display_name, v.draft_text, v.ai_generated
            FROM visit_notes v JOIN appointments a ON a.id = v.appointment_id
            JOIN patients p ON p.id = a.patient_id WHERE v.appointment_id = ?
            """, (rs, row) -> new NoteItem(rs.getObject("appointment_id", UUID.class), rs.getString("display_name"), rs.getString("draft_text"), rs.getBoolean("ai_generated")), appointmentId);
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        return jdbc.queryForMap("""
            SELECT count(*) AS appointments,
                   count(DISTINCT patient_id) AS patients,
                   count(*) FILTER (WHERE starts_at >= now()) AS upcoming
            FROM appointments WHERE practitioner_id = ? AND status = 'BOOKED'
            """, PRACTITIONER_ID);
    }

    @ExceptionHandler(AppointmentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(AppointmentConflictException ex) {
        return Map.of("code", "SLOT_UNAVAILABLE", "message", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException ex) {
        return Map.of("code", "INVALID_APPOINTMENT", "message", ex.getMessage());
    }
}
