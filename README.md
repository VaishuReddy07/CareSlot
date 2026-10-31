# CareSlot

Appointment booking and practice operations for patients and practitioners.

## Local development

Requirements for the full stack: Docker Desktop with Docker Compose v2.

```sh
docker compose up --build
```

On Windows, you can instead run `start-careslot.bat` after Docker Desktop is ready. It starts the stack and opens the web app. Run `stop-careslot.bat` to stop the services; it keeps the PostgreSQL volume.

Open the web app at `http://localhost:5173`. The API health endpoint is `http://localhost:8080/actuator/health`; PostgreSQL is exposed on port `5434`. Compose binds these ports to localhost only.

For the frontend dashboard alone, run `npm ci` and `npm run dev` from `frontend`, then open the URL Vite prints (usually `http://localhost:5173`). Opening `frontend/index.html` directly shows an interactive sample preview; it does not run the React app or connect to the API. Use the localhost URL for the full app.

Compose starts in local demo mode (`CARESLOT_DEMO_MODE=true`): the React dashboard reads and saves sample appointments in PostgreSQL through `/api/demo/appointments`, patient lists and analytics use that database, and the schedule refreshes every 15 seconds. The demo API has no login, so keep demo mode on localhost and use only fictional data. Setting `CARESLOT_DEMO_MODE=false` disables those routes; the secured `/api/appointments` and `/api/assistant/summarize` routes then require a configured JWT issuer and role/scope claims. The AI endpoint returns an unpersisted draft; set `ANTHROPIC_API_KEY` only in the API runtime environment, and require practitioner review before using a real draft.

## Verification status and production requirements

This repository currently provides a local demo and an API foundation. It is not a production healthcare service and must not store real patient information. The local demo API is intentionally unauthenticated and is suitable only for fictional data on a trusted development machine.

Run `mvn verify` in `backend` to run the unit tests and PostgreSQL concurrency test. The concurrency test uses Testcontainers and requires Docker. Run `npm ci && npm run build` in `frontend` for the TypeScript and production bundle check. CI runs these checks and builds the Compose images.

For the local end-to-end smoke check, start the Compose stack and run `node scripts/e2e-smoke.mjs` from the repository root. It checks health, the web response, appointment and patient reads, availability save, analytics, clinical-note save/readback, and 25 simultaneous bookings for the same slot. It creates synthetic records; remove the local database volume with `docker compose down --volumes` when you want a completely fresh demo database.

Demo seed data is removed by the next Flyway migration and is restored only when `CARESLOT_DEMO_MODE=true`. Production mode denies API access when no JWT issuer is configured. This is a fail-closed starting point, not a complete production deployment: production still needs organization and practitioner provisioning, verified identity-provider claim mapping and role tests, fully authenticated frontend flows, operational monitoring and backups, secrets and key rotation, TLS, incident response, data-retention and deletion workflows, and privacy/security review for the jurisdictions where it will operate. The AI endpoint returns an unpersisted draft and requires practitioner review; it is not medical advice.

## Architecture

- `frontend`: responsive React + TypeScript app.
- `backend`: Spring Boot API, PostgreSQL persistence and Flyway migration.
- Appointment overlap is rejected at the database layer using a PostgreSQL exclusion constraint; service transactions provide a clear conflict response under concurrent requests.
- `docker-compose.yml`: local web, API and database services.

## Configuration

Copy `.env.example` to `.env` and set strong local-only values. Never commit secrets. For deployment, provide secrets through the platform secret manager, not image build arguments.
