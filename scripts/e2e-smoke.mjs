import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const baseUrl = process.env.CARESLOT_E2E_BASE_URL ?? 'http://localhost:8080';
const webUrl = process.env.CARESLOT_E2E_WEB_URL ?? 'http://localhost:5173';

async function request(path, options) {
  const response = await fetch(`${baseUrl}${path}`, options);
  const body = await response.text();
  let data;
  try { data = body ? JSON.parse(body) : null; } catch { data = body; }
  return { response, data };
}

async function waitForApi() {
  let lastError;
  for (let attempt = 0; attempt < 30; attempt++) {
    try {
      const { response, data } = await request('/actuator/health');
      if (response.ok && data?.status === 'UP') return;
    } catch (error) { lastError = error; }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`API did not become healthy: ${lastError ?? 'health remained down'}`);
}

function parisDateWithWeekday(daysAhead, weekday) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + daysAhead);
  for (let offset = 0; offset < 8; offset++) {
    const candidate = new Date(date);
    candidate.setUTCDate(candidate.getUTCDate() + offset);
    const label = new Intl.DateTimeFormat('en-US', { timeZone: 'Europe/Paris', weekday: 'long' }).format(candidate);
    if (label === weekday) return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit',
    }).format(candidate);
  }
  throw new Error(`Could not find a future ${weekday}`);
}

function parisOffsetFor(date) {
  const instant = new Date(`${date}T12:00:00Z`);
  const zone = new Intl.DateTimeFormat('en', { timeZone: 'Europe/Paris', timeZoneName: 'shortOffset' }).format(instant);
  const match = zone.match(/GMT([+-]\d{1,2})(?::(\d{2}))?/);
  if (!match) throw new Error(`Unexpected Paris UTC offset: ${zone}`);
  const hours = Number(match[1]);
  const minutes = Number(match[2] ?? 0);
  const sign = hours < 0 ? '-' : '+';
  return `${sign}${String(Math.abs(hours)).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}

await waitForApi();
const web = await fetch(webUrl);
assert.equal(web.status, 200, 'web frontend responds with HTTP 200');

const appointments = await request('/api/demo/appointments');
assert.equal(appointments.response.status, 200);
assert.ok(Array.isArray(appointments.data) && appointments.data.length > 0, 'seed appointments are available');

const patients = await request('/api/demo/patients');
assert.equal(patients.response.status, 200);
assert.ok(Array.isArray(patients.data) && patients.data.length > 0, 'patient directory loads');

const availability = await request('/api/demo/availability');
assert.equal(availability.response.status, 200);
assert.ok(Array.isArray(availability.data) && availability.data.length > 0, 'practitioner availability loads');
const savedAvailability = await request('/api/demo/availability', {
  method: 'PUT', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ slots: availability.data }),
});
assert.equal(savedAvailability.response.status, 200, 'availability can be saved');

const analytics = await request('/api/demo/analytics');
assert.equal(analytics.response.status, 200);
assert.ok(Number(analytics.data.appointments) > 0, 'analytics reads persisted appointments');

const runId = randomUUID();
const date = parisDateWithWeekday(14, 'Wednesday');
const offset = parisOffsetFor(date);
const startsAt = new Date(`${date}T10:00:00${offset}`);
const endsAt = new Date(startsAt.getTime() + 30 * 60_000);
const contenderRequests = Array.from({ length: 25 }, (_, index) => request('/api/demo/appointments', {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({
    patientName: `E2E ${runId} ${index + 1}`,
    visitType: 'Consultation',
    startsAt: startsAt.toISOString(),
    endsAt: endsAt.toISOString(),
  }),
}));
const contenders = await Promise.all(contenderRequests);
const created = contenders.filter(item => item.response.status === 201);
const conflicts = contenders.filter(item => item.response.status === 409);
assert.equal(created.length, 1, 'exactly one concurrent request claims the slot');
assert.equal(conflicts.length, 24, 'all other concurrent requests receive a conflict');

const appointmentId = created[0].data.id;
assert.ok(appointmentId, 'created appointment includes its database id');
const draftText = `Synthetic end-to-end verification ${runId}; practitioner review required.`;
const savedNote = await request(`/api/demo/notes/${appointmentId}`, {
  method: 'PUT', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ draftText }),
});
assert.equal(savedNote.response.status, 200, 'clinical note saves');
const noteList = await request('/api/demo/notes');
assert.ok(noteList.data.some(note => note.appointmentId === appointmentId && note.draftText === draftText), 'clinical note reads back');

console.log('E2E PASS: web, health, appointments, patients, availability, analytics, notes, and 25-way slot concurrency.');
