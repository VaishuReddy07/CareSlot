# CareSlot

CareSlot is an appointment and practice management app for a small clinic. Patients can book appointments, while practitioners can review the schedule, manage availability, view patient records, and save visit notes.

The project includes a React and TypeScript web app, a Spring Boot API, and PostgreSQL. Docker Compose runs the full app on your computer.

## Run CareSlot locally

### What you need

- Docker Desktop with Docker Compose v2
- Git, if you are cloning the repository

### Start the app

Clone the repository, open a terminal in the project folder, then run:

```sh
docker compose up --build --detach --wait
```

On Windows, you can also double-click `start-careslot.bat` after Docker Desktop is ready. The first launch can take a few minutes while Docker downloads and builds the images.

Open these addresses once the containers are ready:

- **CareSlot web app:** http://localhost:5173
- **API health:** http://localhost:8080/actuator/health
- **PostgreSQL:** `localhost:5434`

The web app and API are bound to your local machine. To stop CareSlot and keep its database, run `docker compose down` or double-click `stop-careslot.bat`.

To start with an empty database, run `docker compose down --volumes` before starting again. This deletes the local CareSlot database and its saved records.

## What you can try

CareSlot starts in demo mode with fictional sample data. You can explore appointments, patients, clinical notes, practitioner availability, and analytics. Changes made through the demo API are saved to the local PostgreSQL database. The dashboard refreshes schedule data periodically.

The demo API does not require a login. Keep it on your own computer and use fictional information only. Do not enter real patient data.

The AI assistant can prepare a visit-note draft when an Anthropic API key is configured for the API container. Drafts need practitioner review before use; they are not medical advice.

## Configuration

Docker Compose has local defaults, so you can start the project without creating an environment file. To change them, copy `.env.example` to `.env` and edit the values before starting the containers. For example, set `ANTHROPIC_API_KEY` to enable the AI integration.

Keep API keys and passwords private. Do not commit real credentials or place them in frontend code. For a deployed service, provide secrets through your hosting platform's secret manager.

## Run checks

Build and test the API:

```sh
cd backend
mvn -B verify
```

Build the web app:

```sh
cd frontend
npm ci
npm run build
```

For a full-stack check, start the Docker Compose services, then run this from the project root:

```sh
node scripts/e2e-smoke.mjs
```

The smoke check covers API health, the web app, appointment and patient reads, availability updates, analytics, clinical-note save and readback, and 25 simultaneous booking requests for the same time slot. It uses synthetic data. GitHub Actions runs these checks and builds the Docker images on pushes and pull requests to `main`.

## Project layout

- `frontend/` — React and TypeScript web app
- `backend/` — Spring Boot API and database migrations
- `scripts/e2e-smoke.mjs` — full-stack smoke check
- `docker-compose.yml` — local web, API, and PostgreSQL services

## About production use

CareSlot is a development demo and API foundation. It has not been reviewed or validated for use as a production healthcare service. Demo mode is unauthenticated and is intended only for fictional data on a trusted local machine.

Before using real patient information, the service needs a complete security and privacy review, a tested identity-provider setup, authenticated user flows, appropriate access controls, secure deployment and backups, monitoring, incident response, and data-retention and deletion processes. Requirements depend on where and how the service will operate.
