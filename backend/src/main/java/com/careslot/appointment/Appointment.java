package com.careslot.appointment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id private UUID id;
    @Column(name="practitioner_id", nullable=false) private UUID practitionerId;
    @Column(name="patient_id", nullable=false) private UUID patientId;
    @Column(name="starts_at", nullable=false) private Instant startsAt;
    @Column(name="ends_at", nullable=false) private Instant endsAt;
    @Column(nullable=false, length=24) private String status = "BOOKED";
    @Column(name="reason_code", length=64) private String reasonCode;
    @Column(name="visit_type", nullable=false, length=48) private String visitType = "Consultation";
    @Version private long version;

    protected Appointment() {}
    public Appointment(UUID practitionerId, UUID patientId, Instant startsAt, Instant endsAt, String reasonCode) {
        this.id = UUID.randomUUID(); this.practitionerId = practitionerId; this.patientId = patientId;
        this.startsAt = startsAt; this.endsAt = endsAt; this.reasonCode = reasonCode;
    }
    public Appointment(UUID practitionerId, UUID patientId, Instant startsAt, Instant endsAt, String reasonCode, String visitType) {
        this(practitionerId, patientId, startsAt, endsAt, reasonCode); this.visitType = visitType;
    }
    public UUID getId() { return id; }
    public UUID getPractitionerId() { return practitionerId; }
    public UUID getPatientId() { return patientId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getVisitType() { return visitType; }
}
