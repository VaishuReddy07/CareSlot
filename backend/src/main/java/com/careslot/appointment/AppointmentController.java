package com.careslot.appointment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService service;
    public AppointmentController(AppointmentService service) { this.service = service; }
    public record BookingRequest(@NotNull UUID practitionerId, @NotNull @Future Instant startsAt, @NotNull @Future Instant endsAt, @Size(max=64) String reasonCode) {}
    public record AppointmentView(UUID id, UUID practitionerId, UUID patientId, Instant startsAt, Instant endsAt, String status, String visitType) {
        static AppointmentView from(Appointment a) { return new AppointmentView(a.getId(), a.getPractitionerId(), a.getPatientId(), a.getStartsAt(), a.getEndsAt(), a.getStatus(), a.getVisitType()); }
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_patient')")
    public AppointmentView book(@Valid @RequestBody BookingRequest request, @AuthenticationPrincipal Jwt jwt, @RequestHeader(value="X-Request-Id", required=false) String requestId) {
        String displayName = jwt.getClaimAsString("name");
        if (displayName == null || displayName.isBlank()) displayName = "Patient";
        if (requestId == null || requestId.length() > 80) requestId = UUID.randomUUID().toString();
        return AppointmentView.from(service.bookForSubject(request.practitionerId(), jwt.getSubject(), displayName, request.startsAt(), request.endsAt(), request.reasonCode(), "Consultation", requestId));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_patient','SCOPE_practitioner')")
    public AppointmentView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Appointment appointment = service.get(id);
        String role = jwt.getClaimAsString("role");
        boolean isPractitioner = "practitioner".equals(role);
        UUID callerPatient = UUID.nameUUIDFromBytes(jwt.getSubject().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String practitionerId = jwt.getClaimAsString("practitioner_id");
        boolean ownsAsPractitioner = isPractitioner && appointment.getPractitionerId().toString().equals(practitionerId);
        if (!ownsAsPractitioner && (isPractitioner || !appointment.getPatientId().equals(callerPatient))) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return AppointmentView.from(appointment);
    }
    @ExceptionHandler(AppointmentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> conflict(AppointmentConflictException ex) { return Map.of("code", "SLOT_UNAVAILABLE", "message", ex.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(IllegalArgumentException ex) { return Map.of("code", "INVALID_APPOINTMENT", "message", ex.getMessage()); }
}
