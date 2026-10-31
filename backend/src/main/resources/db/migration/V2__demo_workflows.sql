ALTER TABLE appointments
    ADD COLUMN visit_type VARCHAR(48) NOT NULL DEFAULT 'Consultation';

INSERT INTO patients (id, subject_id, display_name) VALUES
  ('10000000-0000-0000-0000-000000000001', 'demo:sophie-laurent', 'Sophie Laurent'),
  ('10000000-0000-0000-0000-000000000002', 'demo:marc-dubois', 'Marc Dubois'),
  ('10000000-0000-0000-0000-000000000003', 'demo:amelie-moreau', 'Amélie Moreau'),
  ('10000000-0000-0000-0000-000000000004', 'demo:thomas-bernard', 'Thomas Bernard');

WITH demo_times AS (
  SELECT (date_trunc('day', now() AT TIME ZONE 'Europe/Paris') + time '09:00') AT TIME ZONE 'Europe/Paris' AS t1,
         (date_trunc('day', now() AT TIME ZONE 'Europe/Paris') + time '09:30') AT TIME ZONE 'Europe/Paris' AS t2,
         (date_trunc('day', now() AT TIME ZONE 'Europe/Paris') + time '10:15') AT TIME ZONE 'Europe/Paris' AS t3,
         (date_trunc('day', now() AT TIME ZONE 'Europe/Paris') + time '11:00') AT TIME ZONE 'Europe/Paris' AS t4
)
INSERT INTO appointments (id, practitioner_id, patient_id, starts_at, ends_at, status, reason_code, visit_type)
SELECT '20000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, t1, t1 + interval '30 minutes', 'BOOKED', NULL, 'Follow-up' FROM demo_times
UNION ALL
SELECT '20000000-0000-0000-0000-000000000002'::uuid, '00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000002'::uuid, t2, t2 + interval '45 minutes', 'BOOKED', NULL, 'New patient' FROM demo_times
UNION ALL
SELECT '20000000-0000-0000-0000-000000000003'::uuid, '00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000003'::uuid, t3, t3 + interval '30 minutes', 'BOOKED', NULL, 'Consultation' FROM demo_times
UNION ALL
SELECT '20000000-0000-0000-0000-000000000004'::uuid, '00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000004'::uuid, t4, t4 + interval '45 minutes', 'BOOKED', NULL, 'Follow-up' FROM demo_times;
