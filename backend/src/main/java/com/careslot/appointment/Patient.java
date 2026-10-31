package com.careslot.appointment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="patients")
public class Patient {
    @Id private UUID id;
    @Column(name="subject_id", nullable=false, unique=true, length=180) private String subjectId;
    @Column(name="display_name", nullable=false, length=120) private String displayName;
    @Column(name="created_at", nullable=false, insertable=false, updatable=false) private Instant createdAt;
    protected Patient() {}
    public Patient(String subjectId, String displayName) { this.id = UUID.nameUUIDFromBytes(subjectId.getBytes(java.nio.charset.StandardCharsets.UTF_8)); this.subjectId = subjectId; this.displayName = displayName; }
    public UUID getId() { return id; }
}
