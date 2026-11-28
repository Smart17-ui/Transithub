# Release 1 Backlog

**Purpose:** the remaining Release 1 work, each item written so it can be pasted into a GitHub Issue (title, labels, body). Source: the build order in [release-1-onboarding.md](../requirements/release-1-onboarding.md) §7.
**Suggested labels:** `release-1`, plus one area label: `backend`, `frontend`, `infra`, `docs`, `quality`. Add `blocked` where noted.

## Done

| Step | What | Evidence |
|---|---|---|
| 1a | Modules, UUIDv7, error codes, audit trail, idempotency, optimistic locking | 30 tests |
| 1b | Database tenant isolation (runtime role, row-level security, schema guard) | AC-R1-X-01 database layer |
| 2a | Sign-in (OIDC), roles and permissions, tenant scope from roles, first-admin bootstrap; provider-neutral for Keycloak; health endpoint | AC-R1-X-01 HTTP layer; 65 tests |
| 3a | Registration drafts, representatives, change history; encrypted evidence; signature acknowledgement with re-signing | AC-R1-CAP-01…06, ACK-01…04 |
| 3b | Offline sync: retry detection, conflicts (both versions kept, supervisor resolves), quarantine | AC-R1-SYN-01…08 |
| 3c | Submission (immutable submitted version), duplicate flagging, locking | AC-R1-SUB-01…03; 144 tests |
| 5a | Verification: independent assignment, assessments, information requests, recommendation; notification outbox | AC-R1-VER-01…07 |
| 5b | Recommendation review, approve/reject, company established as `TH-ZM-nnnnnn`; direct company creation removed | AC-R1-DEC-01…05, EST-01…03; 164 tests |
| 5c | First Company Administrator invitation (hashed single-use token, 7 days, reissue); company activated on acceptance. **Demo script automated** (`ReleaseOneDemoScriptTest`) | AC-R1-EST-04…06; 173 tests |

---

## To do

### #1 Commit step 2 and open the first pull request to `main`
**Labels:** `quality`
- Commit using the convention in the README (one commit per logical change where practical).
- Open a pull request from `feature/company-registration-management` to `main`. CI and CodeQL run for the first time.
- Enable Dependabot alerts and code scanning in GitHub settings (Settings → Code security).
- **Done when:** CI is green on GitHub and the pull request is merged.

### #2 Export the BP-01.01 rule register into the repo
**Labels:** `docs`
- Add `docs/requirements/bp-01.01-onboarding-register.md` with the full text of BR-ONB-012–274, using the same format as the BP-03 and BP-01.04 registers.
- Check the Release 1 acceptance criteria marked **[check rule text]** against it and raise any differences.
- **Done when:** every Release 1 criterion cites rule text, not only a range.

### #3 Infratel and data-protection answers
**Labels:** `infra`, `docs`. Owner: product owner.
- Work through [hosting-and-data-protection-checklist.md](hosting-and-data-protection-checklist.md) Parts A (Infratel) and B (ODPC or lawyer).
- Record answers; significant ones become ADRs.
- **Done when:** the top five Infratel questions and Part B #1–#4 are answered in writing. **Blocks go-live, not development.**

### #4 Step 2b: staff user management
**Labels:** `backend`
- API for platform administrators to create, disable and re-enable staff users, and to grant or revoke scoped roles (UC-X-IAM-02, -03). Audited, with reasons.
- Create the user in Keycloak through its Admin API, which sends the set-password email, and link it by subject.
- Revocation takes effect on the user's next request.
- **Done when:** an administrator can set up Joseph, Chanda, Mwila and Ruth with their roles, and tests cover separation-of-duty-safe role combinations.

### #5 Step 3a: registration drafts (capture, evidence, acknowledgement) — **DONE**
**Labels:** `backend`
- `CompanyRegistration` aggregate with an explicit lifecycle (ADR-0006).
  - Contains the draft, representatives (one or more) and evidence metadata.
  - On-screen signature acknowledgement; refusal; invalidation after a material change.
- Evidence file storage behind an interface. Local disk in development; Infratel storage later.
- Status-history table; audit on every command.
- **Acceptance criteria:** AC-R1-CAP-01…06, AC-R1-ACK-01…04.

### #6 Step 3b: offline sync API and conflicts — **DONE**
**Labels:** `backend`
- Idempotent sync endpoint keyed by the draft's UUID.
- Readable reference `REG-<year>-<6 digits>` assigned on first sync.
- Sync records; SyncConflict with both versions kept; supervisor resolution; quarantine when the capturer's permission was revoked.
- **Acceptance criteria:** AC-R1-SYN-01…08.

### #7 Step 3c: submission and duplicate flagging — **DONE**
**Labels:** `backend`
- Immutable snapshot of the submitted version; no officer edits after submission.
- Possible-duplicate flag on matching registration number, or matching legal name plus district.
- **Acceptance criteria:** AC-R1-SUB-01…03.

### #7a Release synced data from quarantine
**Labels:** `backend`
- A supervisor reviews a quarantined item and either turns it into a draft (assigned to an active officer) or closes it with a reason. Audited.
- **Done when:** nothing in quarantine can only be viewed; each item can be acted on.

### #8 Step 4: device trial (offline PWA on the platform tablet)
**Labels:** `frontend`. **Blocked** until the tablet model is known and one device is available.
- A minimal PWA covering:
  - capture in airplane mode, with camera evidence;
  - encrypted IndexedDB storage, surviving a reboot;
  - sync to the step 3b API.
- **Done when:** a go or no-go decision is recorded as an ADR. No-go means React Native (ADR-0009).

### #9 Step 5a: verification work — **DONE**
**Labels:** `backend`
- Assign and reassign verification (G1 basic); refuse assignment to the capturer (separation of duties).
- Evidence assessments, information requests (basic), provide information, recommendation.
- **Acceptance criteria:** AC-R1-VER-01…07.

### #10 Step 5b: review, decision and company establishment — **DONE**
**Labels:** `backend`
- Recommendation review (endorse or challenge); approve or reject with reasons; appeal deadline (30-day setting).
- Establish the company in the same transaction, with code `TH-ZM-nnnnnn` from a sequence.
- **Remove** `POST /api/v1/companies` and the `company.create-direct` permission (AC-R1-EST-03).
- **Acceptance criteria:** AC-R1-DEC-01…06, AC-R1-EST-01…03.

### #11 Step 5c: first Company Administrator invitation — **DONE**
**Labels:** `backend`
- Invitation (single-use, 7-day expiry) to the primary representative.
- On acceptance: Keycloak user, `COMPANY_ADMINISTRATOR` assignment and company status Active. Reissue path.
- **Acceptance criteria:** AC-R1-EST-04…06.

### #12 Withdrawal and audit view — **DONE**
**Labels:** `backend`
- Withdrawal before a decision (AC-R1-WDR-01, -02).
- Read-only audit trail endpoint for `audit.view` (UC-X-AUD-01).
- **Evidence:** `POST /api/v1/registrations/{id}/withdrawal` records a required reason and instruction confirmation, transitions status and status history atomically, and `GET /api/v1/registrations/{id}/audit-events` returns a bounded read-only page for users with `audit.view`.

### #13 Notifications (email and SMS), Zambian providers
- The invitation token is stored **sealed** (`sealedToken`, encrypted with `SecretSealer`); the sender unseals it at delivery and should purge sent rows after a retention period.
**Labels:** `backend`, `infra`
- A notification port with email and SMS adapters, choosing providers that satisfy the data-protection answers (#3).
- Used for invitations, information requests and decisions. Development uses a log-only adapter.

### #14 OpenAPI description and `allowedActions` — **DONE**
**Labels:** `backend`
- Publish the OpenAPI document; the frontend generates its client from it (R-14, R-25).
- Every registration response includes `allowedActions` for the current user (R-26, AC-R1-DEC-06).
- **Evidence:** Springdoc serves `/v3/api-docs` and `/v3/api-docs.yaml`; Swagger UI is at `/swagger-ui/index.html`. Registration action lists account for permissions, registration state, assignment and decision separation of duties.

### #15 Step 6: Release 1 frontend
**Labels:** `frontend`. Depends on #8 and #14.
- Field capture and sync screens, back-office queues and registration detail, decisions, invitation acceptance, audit view.
- **In progress:** first online staff-workspace slice is in `frontend/`: Keycloak sign-in, registration create/reopen/edit, server-driven actions, withdrawal, and audit history. Offline capture remains gated by the #8 device trial.
- **Back-office slice done (27 Nov 2026):** work queue with stage tabs, counts and search; registration page with lifecycle progress, representatives, evidence (download, assessment), duplicate review, verifier assignment (only eligible people offered), information request and response with document upload, recommendation, review (endorse/challenge), approve (shows the `TH-ZM` code) and reject (shows the appeal deadline); names instead of user ids throughout. Backend support: `GET /api/v1/registrations` (same visibility rule as opening one), `GET …/{id}/verifier-candidates`, `GET /api/v1/staff/names` (`WorkQueueIntegrationTest`, 193 tests).
- **Draft capture done (27 Nov 2026):** submission checklist in four steps (company, representatives, documents, signatures), add/edit/remove representatives, attach/remove documents (client-side type and 10 MB checks), on-screen signature pad with the declaration, refusal with reason. Every page says who acts next. Screens follow the user's permissions from the new `GET /api/v1/me` (`MeIntegrationTest`). Light and dark mode with a header switch (follows the device until chosen).
- **Still to do here:** sync-conflict resolution and quarantine screens (#7a), invitation acceptance page for the new Company Administrator.
- **Public website done (27 Nov 2026):** Home, Services, About and Contact pages written for passengers and bus companies (no technical terms), mobile menu, light/dark mode. **Contact form** stores enquiries (`POST /api/v1/public/enquiries`, the only public write; V15 `contact_enquiry` with consent; hidden spam field; 5 messages per 10 minutes per address) and staff read and resolve them in the **Enquiries** inbox (`enquiry.manage`: Platform Administrator, Onboarding Supervisor). Tests: `EnquiryIntegrationTest`, `SubmissionRateLimiterTest`. The team is notified through the outbox (`team:support`), which is delivered once #13 exists.

### #16 Step 7: demo script on a real tablet
**Labels:** `quality`
- Run the demo script in the Release 1 spec §1 end to end; fix and sign off.

### #17 Independent code review before each merge — **first review DONE (27 Nov 2026)**
- 10 findings (6 important, 4 minor), all confirmed and fixed, each with a regression test (`ReviewFindingsRegressionTest`, `RegistrationSyncServiceTest`). 183 tests.
**Labels:** `quality`
- Before merging each step, run an independent review pass (for example `/code-review`) and fix confirmed findings.

### #4 addendum (28 Nov 2026, decision SS-15)
- When an administrator sets up a staff member whose email already has a self-created account, **reuse that account** and grant the role (audited). Never create a second login for the same email.

### #18 Clean up unused applicant accounts (decision SS-16)
**Labels:** `backend`
- A scheduled job finds accounts with no roles and no application: warning email at 75 days, deletion (TransitHub record and sign-in account) at 90 days, audited.
- **Done when:** no unused applicant account is kept longer than 90 days.

### #19 Close my account (decision SS-14)
**Labels:** `backend`, `frontend`
- An applicant with no application can close their own account. Until then, "Ask us to close it" goes through the Contact page.
