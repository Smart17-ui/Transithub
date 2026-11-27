# 02 — Use Cases

**Status:** Proposed, v0.1. See [README](README.md) for sources and conventions.

This document has three parts:

1. **Actors:** who interacts with TransitHub.
2. **Use-case catalogue:** the goals each actor achieves, for all 11 business processes and the cross-cutting capabilities. This is the discovery and consolidation step; the entries are one line each.
3. **Detailed use cases:** full specifications for the core BP-01.01 onboarding use cases, the first candidate build slice.

Rules for writing a use case here:
- A use case is a **goal an actor achieves**, not a screen or a single business rule.
- Variations (for example "registration has a missing document") are alternative flows of one use case, not new use cases. This keeps the catalogue small.

---

## 1. Actors

### 1.1 Platform-operator actors
| Actor | Description |
|---|---|
| Platform Administrator | Manages platform-wide settings, platform staff accounts and company operational status |
| Registration Officer | Field officer who captures company registrations, often offline [Approved basis] |
| Verification Officer | Assesses registration evidence; cannot give final approval [Approved basis] |
| Registration Approver | Gives the formal approval or rejection decision [Approved basis] |
| Independent Reviewer | Reviews appeals against rejection; must be independent of the original decision [Approved basis] |
| Subscription Officer | Manages plans and subscriptions, promotions, referrals and commercial agreements [Approved basis: BP-01.04 S11] |
| Platform Finance Officer | Reviews payments, reconciles transactions and prepares refund requests [Approved basis: S11] |
| Authorised Finance Manager | Approves refunds and financial adjustments; **cannot approve a refund they prepared** [Approved basis: S11] |
| Platform Auditor | Read-only investigation of records [Approved basis: S13] |
| Platform Support Agent | Handles support requests from companies |
| Platform Manager | Oversight of all companies; escalations and senior-management reviews |

### 1.2 Transport-company actors (always scoped to one company)
| Actor | Description |
|---|---|
| Company Representative | Legally represents the company during onboarding; acknowledges the registration |
| Company Administrator | Top-level administrator of the company's TransitHub tenant |
| HR / Employee Administrator | Maintains employee records |
| Employee | Any company employee (self-service) |
| Timetable Planner | Designs routes and timetables |
| Timetable Approver | Approves timetables for publication |
| Operations Officer | Creates and schedules trips from the approved timetable and manages daily operations [Approved basis] |
| Driver / Crew Member | Operates a trip; records trip events |
| Fleet Manager | Maintains the vehicle register |
| Maintenance Manager / Technician | Plans and performs maintenance |
| Ticketing Agent | Sells and manages bookings at a counter or by phone |
| Luggage Handler | Registers, tags, loads and hands over luggage |
| Parcel Clerk | Accepts, dispatches and delivers parcels |
| Finance Officer / Finance Approver | Company finance with separation of duties |
| Customer Service Agent | Handles cases and complaints |
| Company Manager | Oversight, reports and corrective actions |

### 1.3 External actors
| Actor | Description |
|---|---|
| Passenger | Searches, books and travels; may self-serve [Open: public channel in release 1?] |
| Parcel Sender / Recipient | Sends, tracks and collects parcels |
| Payment Gateway | External payment provider (mobile money, card) |
| Notification Service | External SMS and email provider |
| E-signature Provider | External signing service for commercial agreements [Proposed] |
| System Scheduler | Time-triggered actions such as renewals, expiries and reminders |

> **Important:** actors are **roles**, not job titles. One person may hold several roles (subject to separation-of-duties rules), and a job title never grants permissions automatically [Approved basis].

---

## 2. Use-case catalogue

### BP-01.01 Company Onboarding and Verification
Rule references are group ranges from the register; see [04-business-rules-analysis.md](04-business-rules-analysis.md) §2.

| ID | Use case | Primary actor | Rules |
|---|---|---|---|
| UC-01.01-01 | Capture company registration (field, offline-capable) | Registration Officer | 012–020, 021–023, 037 |
| UC-01.01-02 | Synchronise offline registrations (sync ≠ submission) | Registration Officer / System | 023, 027–029 |
| UC-01.01-03 | Attach registration evidence | Registration Officer / Applicant | 018 |
| UC-01.01-04 | Acknowledge registration (incl. re-acknowledge after material change; refusal) | Company Representative(s) | 013–017 |
| UC-01.01-05 | Correct / amend a registration (history preserved) | Registration Officer / Applicant | 019, 030–032 |
| UC-01.01-06 | Submit registration (submitted version preserved) | Registration Officer / Applicant | 043–044 |
| UC-01.01-07 | Detect and review possible duplicates | System / Verification Officer | 020, 028 |
| UC-01.01-08 | Verify registration (assignment, independence, deadlines, findings) | Verification Officer | 045–050 |
| UC-01.01-09 | Request additional information (multiple, extensions, overdue, reminders) | Verification Officer | 051–058 |
| UC-01.01-10 | Provide additional information | Applicant / Registration Officer | 051–058 |
| UC-01.01-11 | Recommend, independently review recommendation, and decide | Verification Officer → Reviewer → Registration Approver | 061–067 |
| UC-01.01-12 | Establish company (create Company, generate code) | System | 038, 080 |
| UC-01.01-13 | Appeal a rejection | Company Representative | 068, 074 |
| UC-01.01-14 | Review an appeal (independent) | Independent Reviewer | 069–073, 074–075 |
| UC-01.01-15 | Withdraw a registration (incl. controlled withdrawal after submission) / re-register | Applicant / Registration Officer | 033–036 |
| UC-01.01-16 | Activate company account | Company Representative | 038 (approval ≠ operational status) |
| UC-01.01-17 | View registration status and progress (incl. applicant visibility) | Any authorised actor / Applicant | 060, 092 |
| UC-01.01-18 | Resolve offline synchronisation conflict | Authorised Resolver | 024–026 |
| UC-01.01-19 | Start, save and resume a draft online; transfer a draft | Applicant / Registration Officer | 037, 039–041 |
| UC-01.01-20 | Edit a draft collaboratively | Applicant users / Registration Officer | 042 |
| UC-01.01-21 | Reconsider registration after a successful appeal | Independent Reconsideration Officer → Approver | 076–087 |
| UC-01.01-22 | Reopen a registration decision (controlled, independent) | Authorised Manager → Independent Reviewer | 088–092 |

Continuity, delegation, reassignment, escalation and corrective-action rules (093–274) are **not** onboarding use cases. They are platform SEC/NFR requirements or generic governance mechanisms; see the Cross-cutting section and [04-business-rules-analysis.md](04-business-rules-analysis.md).

### BP-01.02 Employee Administration
| ID | Use case | Primary actor |
|---|---|---|
| UC-01.02-01 | Register employee | HR Administrator |
| UC-01.02-02 | Manage employment lifecycle (probation, confirm, transfer, suspend, terminate) | HR Administrator |
| UC-01.02-03 | Maintain organisation structure (units, positions) | Company Administrator |
| UC-01.02-04 | Assign employee to position (substantive or acting) | HR Administrator |
| UC-01.02-05 | Assign employee responsibilities | Company Manager |
| UC-01.02-06 | Maintain employee information and documents | HR Administrator |
| UC-01.02-07 | Request and approve leave / record unavailability | Employee / Manager |
| UC-01.02-08 | Record attendance and overtime | Supervisor / Employee |
| UC-01.02-09 | Verify operational eligibility (licences, medicals, training) | HR Administrator / System |
| UC-01.02-10 | Publish duty availability roster | Operations Officer |
| UC-01.02-11 | Arrange employee substitution | Supervisor |
| UC-01.02-12 | Hand over and offboard employee | HR Administrator |
| UC-01.02-13 | Self-service: view record, request correction | Employee |
| UC-01.02-14 | Record training and safety events | HR Administrator |
| UC-01.02-15 | Issue and recover company assets and credentials | HR Administrator |
| UC-01.02-16 | Send communication requiring acknowledgement | Company Manager |
| UC-01.02-17 | Link employee to a user account | Company Administrator |

### BP-01.03 Company Configuration Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-01.03-01 | Manage company policies and settings | Company Administrator |
| UC-01.03-02 | Export approved configuration | Company Administrator |
| UC-01.03-03 | Import and validate configuration (branch / environment) | Company Administrator |
| UC-01.03-04 | Resolve configuration conflicts | Company Administrator |

### BP-01.04 Subscription Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-01.04-01 | Manage subscription plans and entitlements (versions, approval, publication, retirement) | Subscription Officer |
| UC-01.04-02 | Subscribe and activate | Company Administrator |
| UC-01.04-03 | Renew subscription (incl. grace period) | System Scheduler / Company Administrator |
| UC-01.04-04 | Change subscription plan | Company Administrator |
| UC-01.04-05 | Cancel subscription | Company Administrator |
| UC-01.04-06 | Reactivate subscription | Company Administrator |
| UC-01.04-07 | Pay for subscription | Company Administrator → Payment Gateway |
| UC-01.04-08 | Request and approve subscription refund (full or partial); does not cancel the subscription | Platform Finance Officer (prepares) / Authorised Finance Manager (approves) |
| UC-01.04-09 | Handle payment dispute; does not trigger a refund by itself | Platform Finance Officer |
| UC-01.04-10 | Run promotional campaign (create, approve, close) | Subscription Officer |
| UC-01.04-11 | Redeem promotional code | Company Administrator |
| UC-01.04-12 | Award referral reward (approval before applying) | System / Subscription Officer |
| UC-01.04-13 | Manage commercial agreement (versions, approval, party acceptance) | Subscription Officer |
| UC-01.04-14 | Sign subscription documents electronically | Company Representative |
| UC-01.04-15 | Investigate commercial / audit matter | Platform Manager / Platform Auditor |
| UC-01.04-16 | Reconcile subscription payments and resolve differences | Platform Finance Officer |
| UC-01.04-17 | Manage subscription knowledge-base articles | Subscription Officer |

Approved decisions: [../requirements/bp-01.04-subscription-payments-register.md](../requirements/bp-01.04-subscription-payments-register.md). Billing exceptions (failed renewals, retries, suspension, invoices, tax, currencies) are still discovery topics (S14).

### BP-01.05 – BP-01.09 (needs further discovery) [Proposed]
| ID | Use case | Primary actor |
|---|---|---|
| UC-01.05-01 | Open, update and close branch | Company Administrator |
| UC-01.06-01 | Suspend company | Platform Administrator |
| UC-01.06-02 | Reinstate company | Platform Administrator |
| UC-01.06-03 | Deactivate company | Platform Administrator |
| UC-01.06-04 | Confirm operational readiness | Platform Administrator |
| UC-01.07-01 | Raise support request | Company Administrator |
| UC-01.07-02 | Resolve support request | Platform Support Agent |
| UC-01.07-03 | Maintain knowledge base | Platform Support Agent |
| UC-01.08-01 | Review company compliance and activity | Platform Manager |
| UC-01.09-01 | Request ownership transfer | Company Representative |
| UC-01.09-02 | Verify and approve ownership transfer | Verification Officer / Approver |

### BP-02 Route and Timetable Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-02-01 | Maintain stops and terminals | Timetable Planner |
| UC-02-02 | Define route (ordered stops, distances, durations) | Timetable Planner |
| UC-02-03 | Draft timetable (departures, days of operation, validity) | Timetable Planner |
| UC-02-04 | Submit and approve timetable | Timetable Approver |
| UC-02-05 | Publish timetable | Timetable Approver |
| UC-02-06 | Amend timetable (new version, history kept) | Timetable Planner |
| UC-02-07 | Withdraw route or timetable | Timetable Approver |
| UC-02-08 | View route and timetable history | Any authorised actor |

### BP-03 Passenger Booking and Ticketing
Grouped by the ten accepted subprocesses. Approved decisions are in [../requirements/bp-03-passenger-booking-register.md](../requirements/bp-03-passenger-booking-register.md) (A–I). The numbering replaces the earlier draft list.

| ID | Subprocess | Use case | Primary actor | Basis |
|---|---|---|---|---|
| UC-03-01 | 03.01 | Register passenger account | Passenger | A |
| UC-03-02 | 03.01 | Sign in and manage profile and account security | Passenger | A |
| UC-03-03 | 03.01 | Recover account or credentials | Passenger | [Open] discovery 12 |
| UC-03-04 | 03.01 | View own booking and ticket history | Passenger | [Open] discovery 13 |
| UC-03-05 | 03.02 | Search journeys and check availability and fares | Passenger / Ticketing Agent | B |
| UC-03-06 | 03.03 | Select and reserve seats | Passenger / Ticketing Agent | C |
| UC-03-07 | 03.03 | Expire unpaid reservation | System | C; [Open] discovery 1–2 |
| UC-03-08 | 03.04 | Buy ticket online (pay, confirm) | Passenger → BP-09 → Payment Provider | D |
| UC-03-09 | 03.05 | Sell ticket at a company office or authorised agent | Ticketing Agent / Authorised Sales Agent | E |
| UC-03-10 | 03.06 | Issue ticket | System (after confirmed payment or purchase) | D, F |
| UC-03-11 | 03.06 | Validate ticket (used by BP-04 boarding) | Conductor / Boarding Officer | F; [Open] discovery 10–11 |
| UC-03-12 | 03.07 | Modify booking (date, seat, passenger) | Passenger / Ticketing Agent | G; transfer [Open] discovery 5 |
| UC-03-13 | 03.07 | Cancel booking | Passenger / Ticketing Agent | G |
| UC-03-14 | 03.07 | Handle bookings on a changed or cancelled trip | System / Ticketing Agent | [Open] discovery 14–15 |
| UC-03-15 | 03.08 | Request ticket refund (workflow; BP-09 approves and settles) | Passenger / Ticketing Agent → BP-09 | H; [Open] discovery 6–7 |
| UC-03-16 | 03.09 | Reconcile ticket sales with financial records | Company Finance Officer (with BP-09) | I |
| UC-03-17 | 03.10 | Report ticket sales by channel | Company Manager | I |

### BP-04 Trip and Operations Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-04-01 | Create trips from approved timetable | Operations Officer |
| UC-04-02 | Assign vehicle to trip | Operations Officer |
| UC-04-03 | Assign crew to trip | Operations Officer |
| UC-04-04 | Check trip readiness (vehicle, crew, documents) | Operations Officer |
| UC-04-05 | Board passengers / validate tickets against manifest | Driver / Crew |
| UC-04-06 | Record departure | Driver / Operations Officer |
| UC-04-07 | Record stop and arrival events | Driver / Crew |
| UC-04-08 | Report operational incident | Driver / Crew |
| UC-04-09 | Delay or cancel trip | Operations Officer |
| UC-04-10 | Replace vehicle or crew on a trip | Operations Officer |
| UC-04-11 | Complete and close trip | Operations Officer |
| UC-04-12 | Monitor daily operations | Operations Officer / Manager |

### BP-05 Fleet and Vehicle Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-05-01 | Register vehicle | Fleet Manager |
| UC-05-02 | Classify vehicle | Fleet Manager |
| UC-05-03 | Define seat layout and facilities | Fleet Manager |
| UC-05-04 | Maintain vehicle documents (with expiry alerts) | Fleet Manager |
| UC-05-05 | Change vehicle status | Fleet Manager / System |
| UC-05-06 | Allocate vehicle to branch | Fleet Manager |
| UC-05-07 | Check vehicle availability | Operations Officer |
| UC-05-08 | Retire / dispose of vehicle | Fleet Manager |

### BP-06 Maintenance Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-06-01 | Plan preventive maintenance schedule | Maintenance Manager |
| UC-06-02 | Report vehicle defect | Driver / Any staff |
| UC-06-03 | Inspect vehicle | Technician |
| UC-06-04 | Raise and assign work order | Maintenance Manager |
| UC-06-05 | Perform and record maintenance (labour, parts) | Technician |
| UC-06-06 | Approve return to service | Maintenance Manager |
| UC-06-07 | View maintenance history | Fleet / Maintenance Manager |

### BP-07 Accompanied Passenger Luggage
| ID | Use case | Primary actor |
|---|---|---|
| UC-07-01 | Register luggage against booking | Luggage Handler / Ticketing Agent |
| UC-07-02 | Assess allowance and charge excess | Luggage Handler → BP-09 |
| UC-07-03 | Tag luggage | Luggage Handler |
| UC-07-04 | Load luggage (handover to crew) | Luggage Handler / Crew |
| UC-07-05 | Hand back luggage at destination | Crew / Luggage Handler |
| UC-07-06 | Report lost, damaged or unclaimed luggage | Passenger / Staff |
| UC-07-07 | Resolve luggage claim | Customer Service Agent |

### BP-08 Unaccompanied Parcel Delivery
| ID | Use case | Primary actor |
|---|---|---|
| UC-08-01 | Quote parcel tariff | Parcel Clerk / Sender |
| UC-08-02 | Accept parcel (sender, recipient, classification) | Parcel Clerk |
| UC-08-03 | Pay for parcel | Sender → BP-09 |
| UC-08-04 | Label parcel | Parcel Clerk |
| UC-08-05 | Dispatch parcel on trip | Parcel Clerk |
| UC-08-06 | Receive parcel at branch | Parcel Clerk |
| UC-08-07 | Track parcel | Sender / Recipient |
| UC-08-08 | Notify recipient | System |
| UC-08-09 | Deliver / release parcel with proof | Parcel Clerk |
| UC-08-10 | Handle failed delivery / return | Parcel Clerk |
| UC-08-11 | Handle parcel claim (lost, damaged) | Customer Service Agent |
| UC-08-12 | Settle parcel charges (e.g. pay-on-collection) [Open] | Parcel Clerk → BP-09 |

### BP-09 Financial Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-09-01 | Process payment request | System (from BP-03/07/08/01.04) |
| UC-09-02 | Confirm payment from gateway | Payment Gateway |
| UC-09-03 | Process refund (with approval) | Finance Officer / Approver |
| UC-09-04 | Record adjustment (with approval) | Finance Officer / Approver |
| UC-09-05 | Cash-up / till reconciliation | Ticketing Agent / Finance Officer |
| UC-09-06 | Reconcile with gateway and bank | Finance Officer |
| UC-09-07 | Post ledger entries | System |
| UC-09-08 | Settle with external parties | Finance Officer |
| UC-09-09 | Close financial period | Finance Approver |
| UC-09-10 | Provide financial reports | Finance Officer |

### BP-10 Customer Service Management
| ID | Use case | Primary actor |
|---|---|---|
| UC-10-01 | Log case or complaint | Customer / Customer Service Agent |
| UC-10-02 | Assign case | Customer Service Supervisor |
| UC-10-03 | Investigate case | Customer Service Agent |
| UC-10-04 | Escalate case | Customer Service Agent / System (SLA) |
| UC-10-05 | Resolve case (incl. compensation request → BP-09) | Customer Service Agent |
| UC-10-06 | Communicate with customer | Customer Service Agent |
| UC-10-07 | Close / reopen case | Customer Service Agent |
| UC-10-08 | Record customer feedback | Customer |

### BP-11 Reporting and Management Oversight
| ID | Use case | Primary actor |
|---|---|---|
| UC-11-01 | View operational reports and dashboards | Company Manager |
| UC-11-02 | View financial reports (reconciled to BP-09) | Company Manager / Finance |
| UC-11-03 | Monitor KPIs | Company Manager |
| UC-11-04 | Review exception reports | Company Manager |
| UC-11-05 | Platform-level oversight across companies | Platform Manager |
| UC-11-06 | Record management finding | Company / Platform Manager |
| UC-11-07 | Track corrective action | Assigned Owner / Manager |

### Cross-cutting
| ID | Use case | Primary actor |
|---|---|---|
| UC-X-IAM-01 | Sign in / sign out | Any user |
| UC-X-IAM-02 | Manage user account | Company / Platform Administrator |
| UC-X-IAM-03 | Grant and revoke roles (scoped to company / branch) | Company / Platform Administrator |
| UC-X-IAM-04 | Delegate authority temporarily | Authority holder |
| UC-X-GOV-01 | Assign / reassign work and accept, partially accept or refuse it (G1) | Supervisor / Assignee |
| UC-X-GOV-02 | Delegate authority and hand over interim accountability (G2) | Authority holder |
| UC-X-GOV-03 | Make emergency appointment and review it retrospectively (G3) | Senior Manager / Independent Reviewer |
| UC-X-GOV-04 | Notify and track acknowledgement, handle failed notification (G4) | System / Recipient |
| UC-X-GOV-05 | Conduct an independent review and record outcome (G5) | Independent Reviewer |
| UC-X-GOV-06 | Raise, complete, verify and reopen corrective action, with success criteria (G6) | Action Owner / Verifier |
| UC-X-GOV-07 | Escalate and conduct management / periodic senior-management review (G7) | System / Manager |
| UC-X-GOV-08 | Review suitability of a responsible person (G8) | Manager / Reviewer |
| UC-X-GOV-09 | Record incident or risk; raise, acknowledge and close risk alert (G9) | Any staff / Risk Owner |
| UC-X-GOV-10 | Activate and deactivate business continuity | Platform Manager |
| UC-X-AUD-01 | View audit trail | Auditor / Manager |
| UC-X-NOT-01 | Acknowledge notification | Any user |

> The **governance** use cases implement mechanisms G1–G9 from [04-business-rules-analysis.md](04-business-rules-analysis.md), which cover BR-ONB-111–274. Each applies to any work item in any module, so the nesting (a corrective action about a review about an appointment…) needs no new use cases [Proposed].

---

## 3. Detailed use cases: BP-01.01 core

These five use cases cover the minimum viable onboarding slice: **capture → verify → decide → establish → appeal**.

### UC-01.01-01 Capture company registration
| | |
|---|---|
| **Primary actor** | Registration Officer |
| **Stakeholders** | Company Representative (wants a fast, correct registration); Platform (wants genuine companies only) |
| **Preconditions** | The officer is authenticated, holds the Registration Officer role, and their device is enrolled for offline use |
| **Trigger** | The officer visits a transport company in the field |
| **Postconditions (success)** | A CompanyRegistration exists in **Draft** with a unique registration reference, company details, representative details, evidence and acknowledgement. It is stored locally if offline and on the server once synchronised |

**Main flow**
1. The officer starts a new registration. The system assigns a **globally unique registration reference** without needing connectivity.
2. The officer enters company identity and business information: legal name, trading name, registration number, address, contacts and business type.
3. The officer records one or more Company Representatives' identities and capacities [Approved basis: multiple representatives].
4. The officer attaches evidence (UC-01.01-03).
5. The representative reviews and acknowledges the registration (UC-01.01-04).
6. The system validates mandatory fields and saves the registration as **Draft**.
7. When connected, the registration is synchronised (UC-01.01-02).

**Alternative flows**
- **6a. Mandatory data missing:** the system saves the registration as an incomplete Draft and lists what is missing. Submission is blocked until complete.
- **1a. Online channel:** the applicant (or an officer at a counter) starts the draft online instead of in the field (UC-01.01-19).
- **5a. Representative refuses to acknowledge:** the refusal is recorded with its reason, and the registration cannot be submitted (BR-ONB-013–017).
- **5b. Material change after acknowledgement:** the acknowledgement is invalidated and must be repeated.
- **7a. Device offline:** the registration stays queued on the device and is synchronised automatically when connectivity returns.

**Exceptions**
- **E1. Officer authorisation revoked before sync:** the server rejects the sync, the registration is quarantined for supervisor review, and nothing is lost [Proposed].

**Business rules:** BR-ONB-xxx [to be traced: reference format, mandatory fields, acknowledgement].

### UC-01.01-02 Synchronise offline registrations
| | |
|---|---|
| **Primary actor** | Registration Officer's device (System) |
| **Preconditions** | Queued registrations exist on the device; connectivity is available |
| **Postconditions** | Each queued registration exists **exactly once** on the server; the device knows each one's server status |

**Main flow**
1. The device sends each queued registration and its evidence, identified by its registration reference (the idempotency key).
2. The server checks whether that reference already exists.
3. If it is new, the server stores it and runs duplicate detection (UC-01.01-07).
4. The server acknowledges each item, and the device marks it as synchronised.

**Alternative flows**
- **2a. Reference already received (a retry):** the server returns the existing result and does not create a duplicate.
- **3a. Possible duplicate company found:** the registration is stored and flagged for the verifier, never silently merged or discarded.
- **4a. Connection lost mid-sync:** items not acknowledged stay queued and are retried safely.

**Exceptions**
- **E1. Conflicting edits:** the registration was changed centrally while it was being edited offline. The server **preserves both versions**, records a SyncConflict, and an **authorised resolver** decides (UC-01.01-18) [Approved basis: BR-ONB-024–026; Open: who is authorised].

**Business rule:** synchronisation never counts as submission, verification or approval [Approved basis].

### UC-01.01-08 Verify registration
| | |
|---|---|
| **Primary actor** | Verification Officer |
| **Preconditions** | The registration is **Submitted**. The verifier is not the officer who captured it [Proposed: separation of duties] |
| **Postconditions** | The registration is **Verified** (recommended for approval) or **Awaiting Information**, with an assessment recorded for each piece of evidence |

**Main flow**
1. The verifier takes a submitted registration from the queue; its status becomes **Under Verification**.
2. The verifier reviews the company details, representative identity, evidence and any duplicate flags.
3. The verifier records an assessment for each evidence item (accepted / insufficient / invalid).
4. The verifier records a recommendation and completes verification; the status becomes **Verified**.

**Alternative flows**
- **3a. Information is insufficient:** the verifier requests additional information (UC-01.01-09) and the status becomes **Awaiting Information**. When the information arrives (UC-01.01-10), the status returns to **Under Verification**.
- **4a. Verifier recommends rejection:** the status still becomes **Verified**, with a negative recommendation; only the Approver decides.

**Exceptions**
- **E1. Verifier unavailable:** the work is reassigned (UC-X-GOV-01); the audit trail keeps both verifiers.

### UC-01.01-11 Decide registration
| | |
|---|---|
| **Primary actor** | Registration Approver |
| **Preconditions** | The registration is **Verified**. The approver is not the verifier and not the capturing officer [Approved basis: separation of verification and approval] |
| **Postconditions** | The registration is **Approved** (and UC-01.01-12 has created the Company) or **Rejected**, with documented reasons |

**Main flow**
1. The approver reviews the registration, the verification assessment and the recommendation.
2. The approver approves, with a rationale.
3. The system runs **Establish company** (UC-01.01-12) in the same transaction.
4. The system notifies the representative and the officer.

**Alternative flows**
- **2a. Reject:** the approver records rejection reasons. The status becomes **Rejected** and the appeal window opens; the representative is notified with the reasons and appeal rights.
- **2b. Return to verification:** the approver sends it back with questions; the status returns to **Under Verification** [Proposed].

**Exceptions**
- **E1. Same registration decided twice concurrently:** only one decision succeeds; the other is refused (optimistic locking).

### UC-01.01-12 Establish company (included)
| | |
|---|---|
| **Primary actor** | System |
| **Preconditions** | The registration was approved in UC-01.01-11 |
| **Postconditions** | A permanent **Company** exists, linked to its registration, with an **auto-generated, unique company code**. Its operational status is **Pending Activation** |

**Main flow**
1. The system generates the next company code using the approved format [Open: format].
2. The system creates the Company from the approved registration data.
3. The system records the link Registration → Company and an audit event.
4. The system publishes a *CompanyEstablished* event (consumed by Identity and Access for account activation, and by BP-01.04 for subscription onboarding).

**Exceptions**
- **E1. Code generation collision:** retry with the next value; never accept a caller-supplied code.
- **E2. Company already established for this registration (a retry):** return the existing Company (idempotent).

### UC-01.01-13 / -14 Appeal a rejection and review it
| | |
|---|---|
| **Primary actors** | Company Representative (appeal); Independent Reviewer (decision) |
| **Preconditions** | The registration is **Rejected** and the appeal window is open [Open: length]. The reviewer took no part in the original verification or decision |
| **Postconditions** | The appeal is **Upheld**, and the registration goes to an independent **Reconsideration** (UC-01.01-21) [Approved basis: BR-ONB-076–087], or **Dismissed**, and the rejection is final unless further challenged or reopened (BR-ONB-085–087) |

**Main flow**
1. The representative lodges an appeal with grounds and optional new evidence; the status becomes **Under Appeal**.
2. The system assigns an independent reviewer.
3. The reviewer examines the original decision, its reasons and the new evidence.
4. The reviewer records a decision and its reasoning; the parties are notified.

**Alternative flows**
- **1a. Appeal lodged after the window closes:** it is refused unless a manager grants an extension [Open].

---

## 4. Open questions on use cases [Open]

1. Verifier independence is approved (BR-ONB-046). **Still open:** the exact independence test (Part F), e.g. can the capturing officer ever verify the same registration?
2. ~~If an appeal is upheld, where does it go?~~ **Answered:** to an independent reconsideration (BR-ONB-076–087).
3. How long is the appeal window, and can it be extended?
4. What is the company code format, for example `TH-000123`, or a code that includes a region?
5. ~~How are offline conflicts resolved?~~ **Partly answered:** both versions are preserved, and an authorised person resolves (BR-ONB-024–026). **Still open:** which role is authorised.
6. Who activates the company account: the Representative by invitation, or the Platform Administrator?
7. Which use cases are in the **first release**? A recommended minimum is BP-01.01 core plus UC-X-IAM-01 to UC-X-IAM-03.
