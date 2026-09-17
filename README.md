# Poultry Prophet — Backend (Spring Boot)

Java/Spring Boot implementation of the Poultry Prophet batch-monitoring prototype. It replaces
the SDD's Node.js/Express stack with an equivalent Spring stack.

Poultry Prophet is a rule-based batch-monitoring and decision-support prototype. It records farm
observations, compares them with configured ranges, calculates provisional indicators, and flags
conditions for manager review. It does not diagnose disease or predict future biological or
fighting performance.

| SDD (Node.js)                | This project (Spring Boot)                    |
|------------------------------|-----------------------------------------------|
| Express.js REST API          | Spring Web (`@RestController`)                |
| Prisma ORM + PostgreSQL      | Spring Data JPA / Hibernate + PostgreSQL      |
| JWT + bcrypt + role access   | Spring Security, jjwt, BCrypt, method security|
| Zod validation               | Jakarta Bean Validation (`@Valid`)            |
| Socket.IO push               | STOMP over WebSocket (`SimpMessagingTemplate`)|
| BullMQ worker                | `@Async` + `@TransactionalEventListener`      |
| PDFKit / csv-stringify       | OpenPDF + hand-rolled CSV                      |
| node-cron                    | (not required by current scope)               |

## Requirements

- **JDK 21+** (developed/tested building on JDK 25; bytecode target is 21).
- A **Supabase** (PostgreSQL) database.
- Maven — use the bundled wrapper (`./mvnw`), no system Maven needed.

## Local setup with Supabase

1. In Supabase, open **Project Settings → Database → Connect** and select the **Session pooler**
   (port `5432`). The session pooler is suitable for a persistent local Spring/JPA process and
   avoids requiring IPv6 support from the local network.
2. Copy the environment template if `.env` does not exist:

   ```bash
   cp .env.example .env
   ```

3. Replace the three Supabase placeholders in `.env` with the pooler host, user, and database
   password shown by Supabase. Copy the host exactly from the Connect dialog; its cluster index
   cannot be inferred from the region. Keep the JDBC prefix and `?sslmode=require`. Generate a
   local JWT signing key with `openssl rand -base64 32` and paste it as `JWT_SECRET`.

The application loads the git-ignored `.env` automatically when it is launched from this backend
directory. `.env.example` is safe to commit; `.env` must remain private.

Example `.env` shape:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_POOLER_HOST:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.YOUR_PROJECT_REF
SPRING_DATASOURCE_PASSWORD=YOUR_DATABASE_PASSWORD
JWT_SECRET=YOUR_BASE64_ENCODED_32_BYTE_SECRET
```

Alternatively, export the same variables in the shell instead of using `.env`:

```bash
# PowerShell example
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="postgres.<project-ref>"
$env:SPRING_DATASOURCE_PASSWORD="<your-db-password>"
$env:JWT_SECRET="<a base64 string of at least 32 bytes>"
```

`spring.jpa.hibernate.ddl-auto=update` creates/updates tables automatically on a local validation
run. Production schema changes require a reviewed migration and backup.
On startup a `DataSeeder` inserts the game fowl lifecycle stages (`brooding`, `ranging`,
`pre-conditioning`, `maintenance`, `conditioning`) and default alert thresholds
(BHI 60–100, BSI 0–40, WFR 1.5–2.5).
The controlled MVP validation scope is daily observations, typed population/loss events, BHI/BSI/WFR
decision-support indicators, configurable ranges, explainable alerts, and manager/handler review.
CRS/individual selection, offline claims, report UI, and advanced charts are deferred.

## Build & run

```bash
chmod +x mvnw                 # Linux/macOS; only needed once
./mvnw clean package          # build (Windows: .\mvnw.cmd clean package)
./mvnw spring-boot:run        # run on http://localhost:8080
# or:
java -jar target/poultry-prophet-backend-0.1.0.jar
```

Verify the running API in another terminal:

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

> **JDK 25 note:** Hibernate's ByteBuddy may not officially recognise very new JDKs. The
> app sets `net.bytebuddy.experimental=true` at startup to allow it. If you hit a bytecode
> error, run on JDK 21 instead.

## Auth quick start

```bash
# 1. Register a manager (open endpoint for bootstrapping)
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"manager@farm.test","password":"password123","fullName":"Farm Manager","role":"MANAGER"}'

# 2. Login as the manager and copy the returned "token"
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"manager@farm.test","password":"password123"}'

# 3. Create a handler already assigned to the manager's farm
curl -X POST localhost:8080/api/handlers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <manager-token>" \
  -d '{"email":"handler@farm.test","password":"password123","fullName":"Handler One"}'
```

Send `Authorization: Bearer <token>` on all other requests. A handler created through
`POST /api/handlers`, or a self-registered handler who accepts a farm invite, may create
batches within that farm.

## API overview

### Module 1 — Data Input & Collection
- `GET  /api/lifecycle-stages` — stage dropdown.
- `GET  /api/handlers` — handlers on your farm (for assignment).
- `POST /api/batches` — register a batch as a manager or farm-assigned handler (1.3); accepts `bloodline` and `source` as
  descriptive Stage-0 metadata (bloodline is collected but **not** used in scoring, Blueprint 5.3).
- `GET  /api/batches?archived=false`, `GET /api/batches/{id}`. `archived=false` (default) returns the
  working list (everything except retired batches); `archived=true` returns retired batches only.
- `PATCH /api/batches/{id}/stage` *(MANAGER)* — advance the batch through the lifecycle (e.g. brooding → ranging).
- `PATCH /api/batches/{id}/archive` *(MANAGER)* — retire a batch (hide from the working list).
- `PATCH /api/batches/{id}/restore` *(MANAGER)* — bring an archived batch back to the working list.
- `POST /api/batches/{id}/records` — record a daily **brooding** entry (1.1); idempotent per (batch, date).
- `GET  /api/batches/{id}/records?limit=14` — recent submissions.
- `POST /api/sync/batch` — legacy sync endpoint retained for later review; offline capability is
  not a validated MVP claim.
- `POST /api/batches/{id}/birds` — band an individual bird (Blueprint 5.4); unique band number per batch.
- `GET  /api/batches/{id}/birds` — list banded birds.
- `POST /api/batches/{id}/birds/{birdId}/ranging` — weekly per-bird ranging milestone
  (weight, health event severity, temperament, C/B+/A/A++ rating); idempotent per (bird, date).
- `GET  /api/batches/{id}/birds/{birdId}/ranging` — that bird's ranging history.

### Module 2 — Data Processing & Analytics
- Rule-based BHI/BSI/WFR indicators are recomputed asynchronously after each observation write.
  Outputs include raw inputs, units, configured ranges/status, formula version, data quality,
  missing-data warnings, and weighted factor contributions.
- `GET  /api/batches/{id}/indicators/latest`, `GET /api/batches/{id}/indicators?limit=14`.
- `GET  /api/thresholds`, `PUT /api/thresholds/{id}` *(MANAGER)* — editable thresholds (2.4).
- Alerts are generated automatically when an indicator breaches its threshold (2.3).
- `GET  /api/batches/{id}/alerts?activeOnly=true`.
- `GET  /api/alerts?activeOnly=true&limit=100` — farm-wide feed across every batch (notifications centre).
- `POST /api/alerts/{id}/acknowledge` *(MANAGER)*.

### Deferred: CRS and Month-5 Selection

The following endpoints remain in the repository for future work but are not part of the controlled
MVP validation or stakeholder decision workflow:
- `GET  /api/batches/{id}/selection` *(MANAGER)* — recomputes scores and returns the **ranked
  selection view**: birds ordered by Conditioning Readiness Score (CRS) desc, each row exposing
  its four sub-scores (Brooding Health Index, Growth, Health History, Behavioural) for
  transparency, plus a suggested advancement cut-line and the system recommendation.
- `POST /api/batches/{id}/selection/birds/{birdId}` *(MANAGER)* — record the breeder's
  confirm/override decision (`advance` true/false). An override (decision ≠ recommendation)
  **requires a `reason`**, recorded as research data.

The engine (`scoring/ScoringService`) is deterministic, transparent and adjustable:
`CRS = 0.30·BHI + 0.30·Growth + 0.20·HealthHistory + 0.20·Behavioural`. All weights, the
mortality band, growth penalty, health deductions, expected-weight curve and cut-line are
**provisional, configuration-driven** starting values under `poultry.scoring.*` in
`application.yml` — not established facts.

### Module 3 — Data Output & Realtime Review
- `GET  /api/batches/{id}/overview` — dashboard composite payload (3.1).
- Report/export endpoints are retained for later review and have no MVP UI.
- WebSocket: connect to `/ws` (SockJS), send `Authorization: Bearer <token>` on CONNECT,
  subscribe to `/topic/farms/{farmId}/alerts` and `/topic/farms/{farmId}/indicators` (3.3).

## Notes on provisional design (per SDD preface)

The BHI/BSI/WFR formulas and severity bands are **provisional**, rule-based, and live in
`AnalyticsService` (weights in `application.yml` under `poultry.analytics`) and
`SeverityClassifier`. Thresholds are DB-backed and editable at runtime. CRS/selection scoring is
deferred and must not be interpreted as a validated recommendation. See
`docs/VALIDATION_ENVIRONMENT_RUNBOOK.md`, `docs/SYNTHETIC_TEST_REPORT.md`, and
`docs/KNOWN_MVP_LIMITATIONS.md` before granting stakeholder access.
