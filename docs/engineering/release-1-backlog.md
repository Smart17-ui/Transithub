# Release 1 Backlog

**Purpose:** the remaining Release 1 work, each item written so it can be pasted into a GitHub Issue (title, labels, body). Source: the build order in [release-1-onboarding.md](../requirements/release-1-onboarding.md) §7.
**Suggested labels:** `release-1`, plus one area label: `backend`, `frontend`, `infra`, `docs`, `quality`. Add `blocked` where noted.

## Done

| Step | What | Evidence |
|---|---|---|
| 1a | Modules, UUIDv7, error codes, audit trail, idempotency, optimistic locking | 30 tests |
| 1b | Database tenant isolation (runtime role, row-level security, schema guard) | AC-R1-X-01 database layer |
| 2a | Sign-in (OIDC), roles and permissions, tenant scope from roles, first-admin bootstrap; provider-neutral for Keycloak; health endpoint | AC-R1-X-01 HTTP layer; 65 tests |

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

### #5 Step 3a: registration drafts (capture, evidence, acknowledgement)
**Labels:** `backend`
- `CompanyRegistration` aggregate with an explicit lifecycle (ADR-0006).
  - Contains the draft, representatives (one or more) and evidence metadata.
  - On-screen signature acknowledgement; refusal; invalidation after a material change.
- Evidence file storage behind an interface. Local disk in development; Infratel storage later.
- Status-history table; audit on every command.
- **Acceptance criteria:** AC-R1-CAP-01…06, AC-R1-ACK-01…04.

### #6 Step 3b: offline sync API and conflicts
**Labels:** `backend`
- Idempotent sync endpoint keyed by the draft's UUID.
- Readable reference `REG-<year>-<6 digits>` assigned on first sync.
- Sync records; SyncConflict with both versions kept; supervisor resolution; quarantine when the capturer's permission was revoked.
- **Acceptance criteria:** AC-R1-SYN-01…08.

### #7 Step 3c: submission and duplicate flagging
**Labels:** `backend`
- Immutable snapshot of the submitted version; no officer edits after submission.
- Possible-duplicate flag on matching registration number, or matching legal name plus district.
- **Acceptance criteria:** AC-R1-SUB-01…03.

### #8 Step 4: device trial (offline PWA on the platform tablet)
**Labels:** `frontend`. **Blocked** until the tablet model is known and one device is available.
- A minimal PWA covering:
  - capture in airplane mode, with camera evidence;
  - encrypted IndexedDB storage, surviving a reboot;
  - sync to the step 3b API.
- **Done when:** a go or no-go decision is recorded as an ADR. No-go means React Native (ADR-0009).

### #9 Step 5a: verification work
**Labels:** `backend`
- Assign and reassign verification (G1 basic); refuse assignment to the capturer (separation of duties).
- Evidence assessments, information requests (basic), provide information, recommendation.
- **Acceptance criteria:** AC-R1-VER-01…07.

### #10 Step 5b: review, decision and company establishment
**Labels:** `backend`
- Recommendation review (endorse or challenge); approve or reject with reasons; appeal deadline (30-day setting).
- Establish the company in the same transaction, with code `TH-ZM-nnnnnn` from a sequence.
- **Remove** `POST /api/v1/companies` and the `company.create-direct` permission (AC-R1-EST-03).
- **Acceptance criteria:** AC-R1-DEC-01…06, AC-R1-EST-01…03.

### #11 Step 5c: first Company Administrator invitation
**Labels:** `backend`
- Invitation (single-use, 7-day expiry) to the primary representative.
- On acceptance: Keycloak user, `COMPANY_ADMINISTRATOR` assignment and company status Active. Reissue path.
- **Acceptance criteria:** AC-R1-EST-04…06.

### #12 Withdrawal and audit view
**Labels:** `backend`
- Withdrawal before a decision (AC-R1-WDR-01, -02).
- Read-only audit trail endpoint for `audit.view` (UC-X-AUD-01).

### #13 Notifications (email and SMS), Zambian providers
**Labels:** `backend`, `infra`
- A notification port with email and SMS adapters, choosing providers that satisfy the data-protection answers (#3).
- Used for invitations, information requests and decisions. Development uses a log-only adapter.

### #14 OpenAPI description and `allowedActions`
**Labels:** `backend`
- Publish the OpenAPI document; the frontend generates its client from it (R-14, R-25).
- Every registration response includes `allowedActions` for the current user (R-26, AC-R1-DEC-06).

### #15 Step 6: Release 1 frontend
**Labels:** `frontend`. Depends on #8 and #14.
- Field capture and sync screens, back-office queues and registration detail, decisions, invitation acceptance, audit view.

### #16 Step 7: demo script on a real tablet
**Labels:** `quality`
- Run the demo script in the Release 1 spec §1 end to end; fix and sign off.

### #17 Independent code review before each merge
**Labels:** `quality`
- Before merging each step, run an independent review pass (for example `/code-review`) and fix confirmed findings.
