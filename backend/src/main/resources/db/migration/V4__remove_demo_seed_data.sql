-- Earlier migrations shipped fictional demo records unconditionally. Remove those rows
-- from every installation; DemoDataSeeder restores them only in explicit demo mode.
DELETE FROM visit_notes
WHERE appointment_id IN (
  SELECT id FROM appointments WHERE practitioner_id = '00000000-0000-0000-0000-000000000001'
);

DELETE FROM appointments
WHERE practitioner_id = '00000000-0000-0000-0000-000000000001';

DELETE FROM patients
WHERE subject_id LIKE 'demo:%';

DELETE FROM practitioner_availability
WHERE practitioner_id = '00000000-0000-0000-0000-000000000001';

DELETE FROM practitioners
WHERE id = '00000000-0000-0000-0000-000000000001';
