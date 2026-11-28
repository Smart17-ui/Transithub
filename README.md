# TransitHub

TransitHub is a multi-company transport management platform for Zambia. Transport companies are onboarded by the platform operator and then run their operations on it: employees, routes and timetables, vehicles and maintenance, trips, ticket sales (at offices and on an online marketplace), luggage, parcels, finance, customer service and reporting. Each company's data is strictly isolated from every other company's.

This is a **monorepo** that holds the backend, the frontend and the shared documentation:

| Folder | What | Stack |
|---|---|---|
| [backend/](backend/) | API and business logic: a modular monolith | Java 25, Spring Boot 4.2, PostgreSQL 18 |
| [frontend/](frontend/) | Staff workspace, a single PWA (ADR-0009). See its [README](frontend/README.md) | React, TypeScript, Vite, Mantine |
| [docs/](docs/) | Analysis, requirements, ADRs and engineering notes for both | Markdown |
| [scripts/](scripts/) | One-time setup scripts (database roles and databases) | SQL |
| [.github/](.github/) | CI per app (`backend-ci.yml`, `frontend-ci.yml`), CodeQL, Dependabot | GitHub Actions |

The backend publishes its API contract as OpenAPI (`/v3/api-docs`), and the frontend generates its TypeScript types from it. Each CI workflow runs only when its own folder changes.

**Current stage:** Release 1, company onboarding end to end. See the [Release 1 specification](docs/requirements/release-1-onboarding.md).

---

## Documentation map

| Where | What |
|---|---|
| [docs/analysis/](docs/analysis/) | System overview, use cases, domain model, business-rules analysis |
| [docs/requirements/](docs/requirements/) | Business-rule registers and the approved Release 1 specification |
| [docs/adr/](docs/adr/) | Architecture Decision Records: what was decided and why |
| [docs/engineering/recommendations.md](docs/engineering/recommendations.md) | Engineering recommendations and the dated **decision log** |
| [docs/engineering/hosting-and-data-protection-checklist.md](docs/engineering/hosting-and-data-protection-checklist.md) | Open questions for Infratel and the Data Protection Commissioner |
| [docs/engineering/release-1-backlog.md](docs/engineering/release-1-backlog.md) | Remaining Release 1 work, ready to become GitHub Issues |

## Architecture in one minute

- **Modular monolith** (ADR-0002): one application, one module per business area under `com.transithub.<module>`. Modules talk only through each other's root package; an architecture test fails the build otherwise.
  - Current modules: `onboarding`, `identity`, and the shared kernel `shared`.
- **Tenant isolation, two layers** (ADR-0003): the application checks permissions, **and** PostgreSQL row-level security filters every tenant table. The application connects as `transithub_runtime`, which owns no tables, so the database rules always apply.
- **Sign-in** (ADR-0017): a self-hosted Keycloak in Zambia issues tokens. TransitHub decides what each user may do (roles scoped to the platform or to one company).
- **Safety nets:**
  - append-only audit trail (ADR-0008);
  - idempotency keys for safe retries and optimistic locking (ADR-0007);
  - time-ordered UUIDv7 identifiers (ADR-0005);
  - every error carries a stable `code` (RFC 9457 ProblemDetail).
- **Hosting** (ADR-0017): Infratel Zambia. Personal data stays in Zambia (Data Protection Act 2021).

---

## Local setup (Windows)

Sections 1–5 set up the **backend**. For the frontend, follow [frontend/README.md](frontend/README.md) once the backend runs.

### 1. Install
- **JDK 25** (for example Eclipse Temurin 25).
- **PostgreSQL 18** on `localhost:5432`.
- *Optional, to sign in for real:* **Keycloak** (see step 5). Tests do not need it.

Docker is **not** required (ADR-0012).

### 2. Create the database roles and databases (once)
Run both scripts as the PostgreSQL superuser, after replacing `CHANGE_ME` in each with a strong password. **Put `CHANGE_ME` back before committing.**

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -h localhost -f scripts\db\create-databases.sql
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -h localhost -f scripts\db\create-runtime-role.sql
```

| Role | Used by | Owns tables? |
|---|---|---|
| `transithub_app` | Flyway migrations | Yes (schema owner) |
| `transithub_runtime` | The running application and tests | No, so row-level security always applies |

### 3. Set environment variables (once)

```powershell
[Environment]::SetEnvironmentVariable('DB_PASSWORD','<transithub_app password>','User')
[Environment]::SetEnvironmentVariable('DB_RUNTIME_PASSWORD','<transithub_runtime password>','User')
```

To run the application (not needed for tests), also create the **file encryption key** for evidence and signatures. Keep it safe: files encrypted with a lost key cannot be recovered.

```powershell
$key = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($key)
[Environment]::SetEnvironmentVariable('TRANSITHUB_STORAGE_KEY',[Convert]::ToBase64String($key),'User')
```

Restart VS Code or your terminal afterwards so it sees them.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_PASSWORD` | Yes | — | Password of `transithub_app` (migrations) |
| `DB_RUNTIME_PASSWORD` | Yes | — | Password of `transithub_runtime` (application) |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/transithub_dev` | Development database |
| `TEST_DB_URL` | No | `jdbc:postgresql://localhost:5432/transithub_test` | Test database |
| `DB_MIGRATION_USERNAME` / `DB_RUNTIME_USERNAME` | No | `transithub_app` / `transithub_runtime` | Role names |
| `TRANSITHUB_STORAGE_KEY` | To run the app | — | Base64 AES-256 key that encrypts evidence and signatures at rest. Tests use a built-in test key |
| `TRANSITHUB_STORAGE_ROOT` | No | `./data/files` | Folder for encrypted files, relative to where the app runs (normally `backend/data/files`, git-ignored) |
| `OIDC_ISSUER` | No | `http://localhost:8180/realms/staff` | Keycloak realm that issues tokens |
| `OIDC_CLIENT_IDS` | No | `transithub-backoffice` | Comma-separated client ids allowed to call the API |
| `OIDC_JWK_SET_URI` | No | `<issuer>/protocol/openid-connect/certs` | Token signing keys |
| `TRANSITHUB_BOOTSTRAP_ADMIN_SUBJECT` | No | — | Keycloak user ID of the first platform administrator (created once at start-up) |
| `TRANSITHUB_BOOTSTRAP_ADMIN_EMAIL` / `_NAME` | No | — | That administrator's email and display name |
| `TRANSITHUB_IDP_ADMIN_CLIENT_SECRET` | For staff management | — | Secret of the `transithub-admin-api` service account (set by `scripts/keycloak/setup-dev-admin-api.ps1`) |
| `TRANSITHUB_APP_URL` | No | `http://localhost:5173/` | Where set-up emails send people when they are done |

### 4. Run the tests

From the `backend` folder:

```powershell
cd backend
.\mvnw test        # unit and integration tests against transithub_test
.\mvnw verify      # what CI runs
```

Flyway creates or updates the schema automatically. Test companies are never deleted (the application has no delete right on companies), so tests use unique codes, and test data accumulating in `transithub_test` is expected.

### 5. Run the application (optional)

```powershell
cd backend
.\mvnw spring-boot:run      # profile "dev", port 8080, database transithub_dev
```

Every endpoint except `/actuator/health` needs a token. To sign in locally, run Keycloak **without Docker**: download the distribution from keycloak.org, unzip it, then:

```powershell
bin\kc.bat start-dev --http-port 8180
```

Create a realm `staff`, a client `transithub-backoffice`, and a user. Set `TRANSITHUB_BOOTSTRAP_ADMIN_SUBJECT` to that user's ID and restart the application to make them the first platform administrator. A step-by-step guide will follow with the frontend work.

#### Self-service sign-up (bus companies create their own account)

1. **Mailpit** catches the emails Keycloak sends (confirm your email, reset password) so nothing leaves your computer. Download `mailpit-windows-amd64.zip` from the Mailpit GitHub releases page, unzip it (for example to `C:\Users\<you>\tools\mailpit`) and run `mailpit.exe`. Inbox: http://localhost:8025 (SMTP on port 1025).
2. With Keycloak running, from the repository root:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\keycloak\setup-dev-signup.ps1
   ```
   It installs the TransitHub theme (`keycloak-theme/transithub`: sign-in pages and emails), turns on account creation, email confirmation and password reset in the `staff` realm, and points Keycloak's email at Mailpit. It asks for the Keycloak admin password. The first time, it asks you to restart Keycloak so the new theme is found.
3. Try it: http://localhost:5173 → **Sign in** → **Create an account** → open the email in Mailpit → confirm. The app registers the new person as an applicant (`POST /api/v1/me/account`): they hold no roles, so staff and company functions stay closed to them.

After changing files in `keycloak-theme/`, run the script again to copy them into Keycloak.

#### Staff management (TransitHub manages sign-in accounts)

Platform Administrators add staff, give and remove roles, and disable accounts from the **Staff** page. TransitHub does this through a Keycloak service account that may only manage the staff realm's users:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\keycloak\setup-dev-admin-api.ps1
```

It creates the confidential client `transithub-admin-api` (once), gives its service account `manage-users`, `view-users` and `query-users`, and stores the secret in your Windows user environment as `TRANSITHUB_IDP_ADMIN_CLIENT_SECRET`. Restart the backend in a new window afterwards. Without the secret, staff management answers 503 `identity-provider-unavailable`. Set-up emails for new staff arrive in Mailpit.

---

## Working conventions

**Commits.** Use [Conventional Commits](https://www.conventionalcommits.org/) and keep each commit to one logical change:

```
feat(onboarding): capture registration drafts offline
fix(identity): refuse tokens issued to unknown clients
test(tenancy): prove pooled connections never keep a previous scope
docs(adr): ADR-0017 host in Zambia at Infratel
chore(deps): bump spring-boot to 4.2.1
```

Types: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`, `ci`. The scope is the module or area.

**Before pushing:** run `.\mvnw verify` in `backend/` (and `npm.cmd run build` in `frontend/` if you changed it). If CI goes red, fixing it comes before any other work (ADR-0012).

**Test data is never deleted** (the application has no delete rights), so it builds up in `transithub_test` across runs. Every test must create its own **unique** data: codes, registration numbers *and names*. Otherwise duplicate detection or uniqueness rules will (correctly) react to leftovers from earlier runs.

**Tests are named after what they prove:** the acceptance criterion (`AC_R1_SYN_02_…`) or business rule (`BR_ONB_046_…`) where one applies.

**Decisions:** any significant technical decision gets a short ADR in `docs/adr/`. Accepted ADRs are never edited, only superseded by a new one.

**Database changes:** only through new Flyway migrations (`backend/src/main/resources/db/migration/V<n>__<description>.sql`). **Never edit an applied migration.** Every new tenant table needs explicit grants to `transithub_runtime`, row-level security and a policy. `TenantIsolationSchemaTest` fails the build otherwise.

**Secrets:** never in code, configuration files or commits; only environment variables. `.env` files are git-ignored.

## Continuous integration

- **CI** (`.github/workflows/ci.yml`) builds and runs all tests against PostgreSQL 18 on every pull request and every push to `main`.
- **CodeQL** (`.github/workflows/codeql.yml`) scans the code for security issues.
- **Dependabot** (`.github/dependabot.yml`) opens weekly pull requests for outdated or vulnerable dependencies.
