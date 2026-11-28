# Release 1: Company Onboarding, End to End

**Status:** **Approved for build** by the product owner (26 November 2026). This is the build specification for Release 1.
**Basis:** [ADR-0001 to 0015](../adr/README.md), the decision log in [recommendations.md](../engineering/recommendations.md), use cases in [02-use-cases.md](../analysis/02-use-cases.md), and BP-01.01 rules BR-ONB-012–092.

> **Change notice, 27 November 2026 (product owner).** Verification is lighter but traceable:
> - **One Compliance Officer** (role code `VERIFICATION_OFFICER`) takes a submitted application, reviews the documents, records **registry verifications** (PACRA and RTSA required; ZRA optional) and **approves or declines** it. The recommendation, independent review and separate approver steps (AC-R1-VER-07, AC-R1-DEC-01, -02) are retired, as are the Recommendation Reviewer and Registration Approver roles.
> - **Separation of duties kept:** whoever prepared, edited or signed for an application can neither take it nor decide on it. Supervisors may still assign or reassign reviews.
> - **Submission now requires** the PACRA registration number, the RTSA Road Service Licence number, the Certificate of Incorporation and the Road Service Licence. Other documents are optional.
> - **Approval requires** every current document accepted, potential duplicates reviewed, and the latest PACRA and RTSA verifications showing a match.
> - Lifecycle: `Draft → Submitted → Under review ⇄ Information requested → Approved | Declined`. `RECOMMENDED` remains only for records created before this change.
>
> Sections 3–5 below keep the original wording for history; where they differ, this notice applies.

> **Rule traceability caveat.** Rule references below are at **group level** (e.g. BR-ONB-012–020). The full text of BR-ONB-012–142 is not yet in the repo. Before building each feature, its criteria are checked against the exact rule text, and any difference is raised with you. Items marked **[check rule text]** are most likely to need it.

---

## 1. Goal

A Registration Officer registers a transport company **offline on a tablet**. The registration syncs, is verified, its recommendation is reviewed, and it is approved. The company is established with code **`TH-ZM-000001`**, and its owner signs in as the first Company Administrator.

### Demo script (the definition of done for Release 1)
1. Joseph (Registration Officer) captures *Kafue Coaches* in airplane mode: company details, two representatives, three evidence photos. Mr Banda signs the declaration on screen.
2. Airplane mode goes off. The registration syncs once (a retry creates nothing new) and gets reference `REG-2026-000001`. Joseph submits it.
3. A supervisor assigns verification to Chanda. Chanda finds the tax clearance expired and requests a new one. Joseph uploads it, and Chanda recommends approval.
4. Joseph, who also holds verifier rights, is **refused** when he tries to verify his own registration.
5. Reviewer Mwila endorses the recommendation. Approver Ruth approves.
6. *Kafue Coaches* is established as `TH-ZM-000001`, status **Pending Activation**. Mr Banda receives an invitation, sets up his login and sees his company. He cannot see anything belonging to any other company.
7. A second registration is **rejected** with reasons. The representative is notified, with the appeal deadline (30 days).
8. The audit trail shows every step: who, what, when and why.

---

## 2. Scope

| In Release 1 | Use case | Deferred (release) |
|---|---|---|
| Capture registration offline and online (draft, save, resume) | UC-01.01-01, -19 (save/resume only) | Draft transfer, collaborative editing (-19 transfer, -20) |
| Attach evidence (photos, PDFs) | UC-01.01-03 | — |
| Representative acknowledgement by on-screen signature; refusal; re-acknowledgement after a material change | UC-01.01-04 | SMS-code confirmation as extra proof (1.1) |
| Correct drafts (history kept) | UC-01.01-05 (drafts only) | Controlled amendments after submission (later) |
| Submit (submitted version preserved) | UC-01.01-06 | — |
| Sync with idempotency; both versions kept on conflict; supervisor resolves | UC-01.01-02, -18 | — |
| Basic duplicate flagging | UC-01.01-07 | Fuzzy matching (later) |
| Assign verification work (G1 basic) | UC-X-GOV-01 (assign, reassign) | Acceptance/refusal, delegation, escalation (G1 full, G2, G7) |
| Verify; request information (basic, one due date); provide information | UC-01.01-08, -09, -10 | Multiple requests, extensions, reminders, overdue escalation (later) |
| Recommend → independent review of recommendation → decide (approve / reject) | UC-01.01-11 | — |
| Establish company and generate code | UC-01.01-12 | — |
| Invite first Company Administrator; first sign-in | UC-01.01-16, UC-X-IAM-01 | Full company user management (Release 2) |
| Withdraw before a decision | UC-01.01-15 | Re-registration linking (later) |
| Registration status and progress | UC-01.01-17 (staff view) | Applicant self-service portal: **moved into Release 1, see §9** |
| Platform staff accounts and scoped roles | UC-X-IAM-01 to -03 | Delegation (UC-X-IAM-04) |
| Audit trail view | UC-X-AUD-01 (basic) | — |
| Notifications: email and SMS for invitation, information request, decision | UC-X-GOV-04 (basic) | Delivery tracking, acknowledgement, failure escalation |
| — | — | **Release 1.1:** appeal, independent appeal review, reconsideration (UC-01.01-13, -14, -21) |
| — | — | Later: decision reopening (-22), continuity and governance G2–G9, BP-01.04 subscription onboarding |

---

## 3. Roles and permissions (platform scope)

| Role | Permissions (examples) |
|---|---|
| Registration Officer | `registration.capture`, `registration.edit-draft`, `registration.submit`, `registration.withdraw`, `registration.provide-information` |
| Registration Supervisor | `registration.assign-verification`, `registration.resolve-sync-conflict`, `registration.view-all` |
| Verification Officer | `registration.verify`, `registration.request-information`, `registration.recommend` |
| Recommendation Reviewer | `registration.review-recommendation` |
| Registration Approver | `registration.approve`, `registration.reject` |
| Platform Administrator | `platform-user.manage`, `role.assign`, `audit.view` |
| Company Administrator (company scope) | `company.view-own` (Release 1 minimum) |

**Separation of duties for one registration** (checked against the record, ADR-0004):
- The **verifier** is not the capturer. "Capturer" includes anyone who edited the registration or recorded an acknowledgement on a representative's behalf.
- The **recommendation reviewer** is neither the capturer nor the verifier.
- The **approver** is not the capturer, the verifier or the reviewer.
- The **conflict resolver** is neither of the two editors in the conflict.

One person may hold several roles. The check is per registration, not per role. **[check rule text: BR-ONB-046, 062]**

---

## 4. Lifecycle (Release 1 subset)

`Draft → Submitted → UnderVerification ⇄ AwaitingInformation → Recommended → (review endorses) → Approved | Rejected`
- `Recommended → UnderVerification` when the reviewer challenges the recommendation.
- `Draft | Submitted | UnderVerification | AwaitingInformation | Recommended → Withdrawn` (any state before a decision).
- `Rejected` records `appealDeadline = notifiedAt + 30 days` (a platform setting). Appeal handling arrives in 1.1.
- **Sync never changes status** (a synced draft is still a Draft).

---

## 5. Acceptance criteria

Test names cite the criterion ID, for example `AC_R1_CAP_03_missingMandatoryFieldsBlockSubmission`.

### 5.1 Capture (UC-01.01-01, -03, -05, -19) — BR-ONB-012–023, 037, 039–041
- **AC-R1-CAP-01** Given an officer with `registration.capture` and a tablet with no connection, when they start a registration, then a draft is created on the device with a **UUIDv7** ID and saved encrypted, with no network needed.
- **AC-R1-CAP-02** Given a draft, when the officer adds company details, **one or more** representatives and evidence files, then all are stored with the draft and survive closing the app and restarting the tablet.
- **AC-R1-CAP-03** Given a draft missing mandatory data, when the officer tries to submit, then submission is blocked and the missing items are listed. **[check rule text: mandatory field list, BR-ONB-012–020]**
- **AC-R1-CAP-04** Given an online staff user, when they create a draft in the browser, save it and return later, then it resumes exactly as saved.
- **AC-R1-CAP-05** Given a draft that is corrected, when a field changes, then the previous value remains in the draft's change history (who, when, old → new).
- **AC-R1-CAP-06** Given evidence over the size limit (default 10 MB per file) or of an unsupported type, then it is refused with a clear message. **[proposed limit]**

### 5.2 Acknowledgement (UC-01.01-04) — BR-ONB-013–017
- **AC-R1-ACK-01** Given a complete draft, when a representative reads the declaration and signs on screen, then an acknowledgement is recorded with the representative, signature image, declaration-text version, draft version and timestamp. This works offline.
- **AC-R1-ACK-02** Given a representative refuses, then the refusal and its reason are recorded and the registration cannot be submitted.
- **AC-R1-ACK-03** Given an acknowledged draft, when a **material** field changes (company identity, registration number, representatives, or evidence), then all acknowledgements are invalidated and must be repeated before submission. **[check rule text: definition of material change]**
- **AC-R1-ACK-04** Given several representatives, then submission requires an acknowledgement from each representative marked as required. **[check rule text: all vs designated representatives]**

### 5.3 Synchronisation and conflicts (UC-01.01-02, -18) — BR-ONB-023–029
- **AC-R1-SYN-01** Given queued drafts, when connectivity returns, then each is sent with its UUID as the idempotency key and the device marks it synced only after the server acknowledges it.
- **AC-R1-SYN-02** Given the same draft sent twice (a retry), then exactly one registration exists on the server and the second call returns the first result.
- **AC-R1-SYN-03** Given a first successful sync, then the server assigns a readable reference `REG-<year>-<6 digits>` and the device shows it.
- **AC-R1-SYN-04** Given a sync, then the registration's status is unchanged (sync ≠ submission), and a sync record (device, time, items, outcome) is stored.
- **AC-R1-SYN-05** Given the server copy changed since the device last synced, when the device syncs an edit, then **both versions are preserved**, a SyncConflict is raised, and the registration cannot be submitted until it is resolved.
- **AC-R1-SYN-06** Given a SyncConflict, when a Registration Supervisor who made neither edit chooses the version (or field-by-field values) with a reason, then the resolution is recorded and both originals are kept.
- **AC-R1-SYN-07** Given the officer's capture permission was revoked before the sync, then the server accepts the data into **quarantine** for supervisor review: nothing is lost, and it is not treated as a valid draft.
- **AC-R1-SYN-08** Given a lost connection mid-sync, then unacknowledged items stay queued and are retried with no duplicates.

### 5.4 Submission and duplicates (UC-01.01-06, -07) — BR-ONB-020, 028, 043–044
- **AC-R1-SUB-01** Given a complete, acknowledged, conflict-free draft, when the officer submits, then the status becomes Submitted and an **immutable snapshot** of the submitted version is stored.
- **AC-R1-SUB-02** Given a submission whose registration number, or legal name plus district, matches an existing registration or company, then it is flagged as a **possible duplicate** for the verifier. It is never merged or discarded.
- **AC-R1-SUB-03** Given Submitted or later, then the officer can no longer edit it (corrections come through information requests).

### 5.5 Verification (UC-01.01-08, -09, -10; G1 basic) — BR-ONB-045–058
- **AC-R1-VER-01** Given a Submitted registration, when a supervisor assigns a Verification Officer, then the assignment (assignee, assigned by, when, reason) is recorded and the status becomes UnderVerification.
- **AC-R1-VER-02** Given the capturer of a registration also holds `registration.verify`, when they are assigned to it or try to verify it, then this is **refused** with the problem code `separation-of-duties`.
- **AC-R1-VER-03** Given verification is in progress, when the verifier records an assessment per evidence item (accepted / insufficient / invalid) with findings, then each is stored with the verifier and time.
- **AC-R1-VER-04** Given insufficient evidence, when the verifier requests information with a description and due date, then the status becomes AwaitingInformation and the officer is notified by email/SMS.
- **AC-R1-VER-05** Given AwaitingInformation, when the officer uploads the information, then it is linked to the request, the status returns to UnderVerification and the verifier is notified.
- **AC-R1-VER-06** Given a supervisor reassigns verification, then both assignments remain in history and the new assignee must also pass the separation-of-duties check.
- **AC-R1-VER-07** Given verification complete, when the verifier records a recommendation (approve / reject) with reasons, then the status becomes Recommended.

### 5.6 Review and decision (UC-01.01-11) — BR-ONB-059–067
- **AC-R1-DEC-01** Given Recommended, when a Recommendation Reviewer (not the capturer or verifier) **endorses** it, then it becomes available to approvers.
- **AC-R1-DEC-02** Given Recommended, when the reviewer **challenges** it with comments, then the status returns to UnderVerification with the comments visible to the verifier.
- **AC-R1-DEC-03** Given an endorsed recommendation, when an Approver who is not the capturer, verifier or reviewer approves with a rationale, then the status becomes Approved and the company is established **in the same transaction** (5.7).
- **AC-R1-DEC-04** Given an endorsed recommendation, when the Approver rejects with reasons, then the status becomes Rejected, `appealDeadline` = notification time + 30 days (a platform setting), and representatives are notified with the reasons and the deadline.
- **AC-R1-DEC-05** Given two approvers act at the same moment, then exactly one decision succeeds and the other receives HTTP 409 `concurrent-modification`.
- **AC-R1-DEC-06** Given any user, the approve and reject actions appear in `allowedActions` only when that user may perform them on that record.

### 5.7 Establish company and first administrator (UC-01.01-12, -16) — BR-ONB-038, 080
- **AC-R1-EST-01** Given approval, then a Company is created from the approved data with the next code `TH-ZM-nnnnnn` from a database sequence, linked to the registration, with operational status **Pending Activation**.
- **AC-R1-EST-02** Given establishment is retried for the same registration, then the existing company is returned and no second company or code is created.
- **AC-R1-EST-03** Given no API accepts a client-supplied company code, then the temporary `POST /api/v1/companies` endpoint is **removed**.
- **AC-R1-EST-04** Given a newly established company, then the representative marked as primary contact receives an **invitation** (SMS and/or email) with a single-use link that expires after 7 days. **[proposed expiry]**
- **AC-R1-EST-05** Given the invitation is accepted, when Mr Banda creates his login with the identity service, then he becomes **Company Administrator** of that company only, and the company's status becomes **Active**. **[check: does activation need further conditions, e.g. a subscription? Assumed not in Release 1]**
- **AC-R1-EST-06** Given an expired or used invitation, then it is refused, and a supervisor can reissue it.

### 5.8 Withdrawal (UC-01.01-15) — BR-ONB-033–036
- **AC-R1-WDR-01** Given any state before a decision, when an authorised officer withdraws with a reason on the representative's instruction, then the status becomes Withdrawn and the data is retained.
- **AC-R1-WDR-02** Given Withdrawn, then no further action is possible on that registration.

### 5.9 Cross-cutting (ADR-0003, 0006–0008)
- **AC-R1-X-01 Tenant isolation:** given two established companies A and B, when A's administrator requests any of B's data by ID or list, then it returns 404 (not 403) and the database's row-level security also blocks it in a test that bypasses the application filter.
- **AC-R1-X-02 Audit:** every command in 5.1–5.8 writes an audit event (actor, action, record, reason, timestamp, correlation id) in the same transaction. Audit rows cannot be updated or deleted by the application role.
- **AC-R1-X-03 Status history:** every status change has a history row (from, to, actor, reason, time).
- **AC-R1-X-04 No status-setting endpoint:** the API exposes only commands. An architecture test fails if any endpoint accepts a `status` field.
- **AC-R1-X-05 Idempotency:** every create command accepts `Idempotency-Key`. A replay returns the same response, and the same key with a different body returns 422.
- **AC-R1-X-06 Errors:** every error is RFC 9457 ProblemDetail with a stable `code`.
- **AC-R1-X-07 Module boundaries:** the architecture test passes (onboarding, identity, governance, notification, audit, and shared modules).

---

## 6. Non-functional targets (Release 1)

| Area | Target |
|---|---|
| Offline | 50 drafts with 10 photos each stored on a tablet; survives a reboot; sync of one draft under 3G conditions completes or resumes |
| Security | Staff MFA through the identity service; evidence encrypted at rest on device and in storage; HTTPS only |
| Performance | Back-office pages respond in < 1 s at Release 1 volumes |
| Browser and device | Platform-issued Android tablet (Chrome) plus current desktop Chrome / Edge |
| Language | English. All user-facing text in translation files (i18n-ready) |

---

## 7. Build order (each step ends with working, tested code on `main`)

1. **Foundation:** restructure into modules (`company` → `onboarding`) with the architecture test; UUIDv7; audit and status-history tables; idempotency; ProblemDetail codes; RLS scaffolding with a cross-tenant test.
2. **Identity:** choose the identity provider (ADR-0016); resource-server security; roles, permissions and separation-of-duties checks; platform staff seeding.
3. **Registration backend:** draft, capture, evidence storage, acknowledgement, submission, sync API with conflicts and quarantine.
4. **Device trial** (in parallel with step 3): PWA offline capture on the actual tablet; go/no-go decision.
5. **Verification and decision backend:** assignment, verification, information request, recommendation review, approve/reject, establish company, invitation.
6. **Frontend:** field capture and sync screens, back-office queues and registration detail with `allowedActions`, audit view.
7. **Demo script run** on a real tablet, then fix and sign off.

---

## 8. Assumptions to confirm (raised during build if the rule text differs)

1. ~~Independent review of the recommendation is a separate step~~ **Confirmed:** the reviewer is a separate person (four-person chain: capturer, verifier, reviewer, approver).
2. "Material change" means company identity, registration number, representatives or evidence.
3. Every representative marked "required" must acknowledge.
4. Activation needs only the first administrator's sign-in, with no subscription condition in Release 1.
5. Proposed limits: 10 MB per evidence file, 7-day invitation expiry, 30-day appeal window (all settings).

---

## 9. Self-service company sign-up (pulled into Release 1, product owner, 27 November 2026)

The applicant portal moves from "later" into Release 1. It replaces the interim `/register` request form. Officer field capture stays for companies that need help.

| # | Situation | Decision |
|---|---|---|
| SS-1 | A bus company owner clicks "Register your company" | They create an **applicant account** and fill in the application themselves; they can save and return later. |
| SS-2 | The application is approved | The **same login** receives the Company Administrator role. There is no second sign-up; the invitation step is replaced for self-service applicants. |
| SS-3 | Two people from the same company each start an application | **Both may apply.** The existing duplicate check flags it; the Compliance Officer keeps one and declines the other with a reason. Nobody is told that another application exists. |
| SS-4 | The company has several directors who must sign | Each director receives a link (SMS or email) to review and sign **on their own phone**. Until notifications (#13) exist, they sign on the applicant's screen. |
| SS-5 | The applicant creates an account | **Email and password**; the email is confirmed by a link before the application can be submitted. The phone number is collected and verified by SMS once SMS sending (#13) exists. |
| SS-6 | Second security step (authenticator code) | **Not while applying.** Required from the moment the account becomes Company Administrator. |
| SS-7 | The applicant signs in while the application is being checked | A **progress timeline** (Submitted, Being checked, Decision), any requests from TransitHub, and the decision with reasons. Never the checker's identity or internal notes. |
| SS-8 | The Compliance Officer asks for a clearer document | The applicant sees the request **in their account** (and by email once sending exists), uploads the document and replies there; it is recorded on the application. |
| SS-9 | The applicant spots a mistake after submitting | The submitted version stays locked. **Before checking starts** they can pull it back to draft, correct it and submit again (a new submitted version). Once checking has started, they send a message and the Compliance Officer requests the correction. |
| SS-10 | The applicant decides not to go ahead | They can **withdraw it themselves** at any time before the decision, giving a short reason; audited like staff withdrawals. |
| SS-11 | A colleague wants to help before approval | **One applicant account per application.** Colleagues are added by the Company Administrator after approval. |
| SS-12 | The application is declined | The applicant **keeps the account**, sees the reasons and appeal deadline, and may appeal (Release 1.1) or start a new application. |
| SS-13 | Where sign-up starts | **Both:** "Register your company" on the website opens the Create account page directly, and the sign-in page keeps a "Registering a bus company? Create an account" link, with notes for travellers and staff. |
| SS-14 | A traveller creates an account by mistake | After confirming their email they are told the account is for bus companies and that travellers can book as guests, with a way back to the home page and a way to close the account. |
| SS-15 | A staff member signed up themselves before being set up | The administrator **reuses that account** (found by email) and grants the staff role; recorded in the audit trail. A self-created account never gains a role by itself. |
| SS-16 | An account is created but no application is ever started | Warning email at 75 days; **deleted at 90 days** if still no application (data minimisation, Data Protection Act). |

**Build progress (28 November 2026).** Step 1 of the self-service build is in place: account creation, email confirmation and password reset on the sign-in service with TransitHub's look (`keycloak-theme/transithub`, set up by `scripts/keycloak/setup-dev-signup.ps1`); `POST /api/v1/me/account` registers a person with a confirmed email as a TransitHub user **with no roles** (refused with `email-not-verified` otherwise; audited as `user.self-registered`); the app shows applicants an "Account ready" home instead of the staff work queue. Next: applicant ownership of an application (step 2) and the applicant API (step 3).

**Build progress (28 November 2026, steps 2 and 3).** Self-service applications belong to their applicant (`applicant_user_id`, channel `SELF_SERVICE`, migration V16). A signed-in user without any role runs in the new **applicant scope**; row-level security then shows and changes only their own application, its representatives, documents, signatures, submissions and history, and lets them write audit events about it only. One open application per applicant (unique index; 409 `application-already-open`). Applicant API `/api/v1/my/applications`: start, view with progress timeline, details, directors, documents (upload, download, remove), on-screen signature (SS-4 fallback), submit (duplicates flagged afterwards in platform scope and never shown to the applicant), take back before checking starts (SS-9), withdraw with a reason (SS-10). Responses never show who is checking, internal notes or duplicates. Tests: `ApplicantApplicationIntegrationTest` (journey, take-back refused once checking starts, cross-applicant isolation through the API and directly in the database, one open application, staff refused, incomplete submission).
Still to build for self-service: answering information requests in the account (SS-8), director signing links (SS-4, needs #13), approval making the applicant Company Administrator (SS-2) with the authenticator step (SS-6), and the applicant screens (step 6).

**Build progress (28 November 2026, step 6).** Applicant screens: the account home starts an application or continues the open one (earlier ones listed); "My application" (`/application/:id`) shows the stage in plain words with a four-step progress bar, the checklist, company details, directors and owners (add, edit, remove, sign on screen), documents (upload, download, remove), the history, and Submit, Make changes (take back) and Withdraw with confirmations; a decline shows its reasons and the appeal deadline. The staff capture dialogs are reused through a per-application API base path, with applicant wording and no "declines to sign" option.
