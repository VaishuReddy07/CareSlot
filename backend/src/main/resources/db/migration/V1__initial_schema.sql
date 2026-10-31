CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE practitioners (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  display_name VARCHAR(120) NOT NULL,
  specialty VARCHAR(120) NOT NULL,
  timezone VARCHAR(80) NOT NULL DEFAULT 'Europe/Paris',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO practitioners (id, display_name, specialty, timezone)
VALUES ('00000000-0000-0000-0000-000000000001', 'Dr. Élodie Laurent', 'Cardiology', 'Europe/Paris');

CREATE TABLE patients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subject_id VARCHAR(180) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE appointments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  practitioner_id UUID NOT NULL REFERENCES practitioners(id),
  patient_id UUID NOT NULL REFERENCES patients(id),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED','CANCELLED','COMPLETED')),
  reason_code VARCHAR(64),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at),
  EXCLUDE USING gist (practitioner_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&) WHERE (status = 'BOOKED')
);
CREATE INDEX appointments_patient_start_idx ON appointments(patient_id, starts_at DESC);
CREATE INDEX appointments_practitioner_start_idx ON appointments(practitioner_id, starts_at);

CREATE TABLE audit_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_subject VARCHAR(180) NOT NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id UUID,
  request_id VARCHAR(80),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_resource_idx ON audit_events(resource_type, resource_id, occurred_at DESC);

CREATE TABLE visit_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id UUID NOT NULL UNIQUE REFERENCES appointments(id),
  practitioner_subject VARCHAR(180) NOT NULL,
  draft_text TEXT NOT NULL,
  ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
