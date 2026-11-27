# 04 — Business Rules Analysis: what the 263 BP-01.01 rules actually describe

**Decision (26 Nov 2026):** the classification, G1–G9 and the BR-ONB-275 recommendation below are **agreed** by the product owner.

**Status:** Proposed, v0.1 (26 November 2026). For validation.

**Source:** *TransitHub Consolidated Business Rules and Decisions Register* (26 Nov 2026). Rule ranges are as given there. Titles for BR-ONB-012 to 142 are group-level only and still need line-by-line reconciliation, so the group boundaries below are approximate where the register says "approved sequence".

This document does **not** change any approved rule. It classifies the approved rules so that the system, and the work still to do, can be understood, designed and tested.

---

## 1. Key finding

**The 263 rules describe two very different things:**

| | Rules | Approx. count | What they describe |
|---|---|---:|---|
| **A** | BR-ONB-012 – 092 | ~81 | **How a company gets registered**: capture, offline sync, corrections, verification, decision, appeal, reconsideration, reopening. This is genuinely BP-01.01 business behaviour. |
| **B** | BR-ONB-093 – 110 | ~18 | **Platform security and resilience**: record access, emergency access, access reviews, retention, backup, disaster recovery, RTO/RPO, continuity. These are *security (SEC)* and *non-functional (NFR)* requirements for the whole platform, not onboarding rules. |
| **C** | BR-ONB-111 – 274 | ~164 | **How the organisation holds people accountable**: incidents, risks, alerts, delegation, reassignment, acceptance and refusal, escalation, emergency appointments, reviews, corrective actions, reopening. These are **generic governance mechanisms** that were discovered *in the context of* onboarding continuity, but they apply to every module. |

So roughly **one third** of the rules define what onboarding does. The other two thirds define platform-wide security, resilience and accountability.

This matters because:
1. **Understanding the system:** BP-01.01 is much smaller than "263 rules" suggests. It is about 80 rules, and they map cleanly onto about 20 use cases.
2. **Avoiding endless rules:** the rules in group C repeat the **same pattern** at deeper and deeper levels. Recognising the pattern stops the recursion (see §3).
3. **Building it:** the group C mechanisms are built **once**, as a shared governance module, and then reused by BP-02 to BP-11 without writing new rules for each.

---

## 2. Group A: core onboarding rules → use cases

| Rule group | Rules | Subject | Use cases ([02-use-cases.md](02-use-cases.md)) | Domain entities ([03-domain-model.md](03-domain-model.md)) |
|---|---|---|---|---|
| A1 | 012 – 020 | Drafts, acknowledgement, re-acknowledgement after material change, refusal to acknowledge, documents, change history, **multiple representatives**, duplicate detection | UC-01.01-01, -03, -04, -05, -07 | CompanyRegistration, RegistrationRepresentative (1..*), RepresentativeAcknowledgement, RegistrationEvidence, RegistrationRevision, DuplicateFlag |
| A2 | 021 – 029 | Offline capture, local storage, secure sync, **conflict detection and preservation of both versions, authorised conflict resolution**, sync history, cross-device duplicates, offline data protection. *Sync is not submission.* | UC-01.01-01, -02, -18 | SyncRecord, SyncConflict |
| A3 | 030 – 044 | Corrections, amendments after submission, withdrawal (incl. after submission), re-registration, **registration approval ≠ operational status**, multiple initiation channels, **online drafts**, draft access and transfer, **collaborative / concurrent editing**, submitted-version preservation | UC-01.01-05, -06, -15, -19, -20 | SubmittedVersion, DraftCollaborator |
| A4 | 045 – 060 | Verification assignment, **verifier independence**, workload, deadlines, findings, evidence, information requests (multiple, overdue, extensions, reminders), status while awaiting information, **applicant visibility of progress** | UC-01.01-08, -09, -10, -17 | VerificationAssignment, VerificationAssessment, InformationRequest |
| A5 | 061 – 073 | Recommendation, **independent review of the recommendation**, authorised final decision, reasons and evidence, notifications, audit, rejection, appeal initiation, appeal independence, findings, outcomes | UC-01.01-11, -12, -13, -14 | RecommendationReview, RegistrationDecision, Appeal |
| A6 | 074 – 087 | Appeal deadlines, appeal record integrity, **reconsideration after a successful appeal** (independent, own findings and final decision), company establishment after reconsideration, further investigation, further challenge, reopening of reconsideration | UC-01.01-14, -21, -12 | Reconsideration, ReconsiderationDecision |
| A7 | 088 – 092 | **Controlled reopening of registration decisions**: independent assignment, deadlines, outcomes, communications | UC-01.01-22 | DecisionReopening |

**Decisions that answer earlier open questions:**
- *What happens when an appeal is upheld?* It goes to a **reconsideration**, which is independent and has its own final decision, not straight back to the original verifier (A6).
- *How are offline conflicts resolved?* Both versions are preserved and an **authorised person** resolves the conflict (A2). The exact authority is still a Part F detail.
- *Is separation of duties absolute?* At least verifier independence (BR-ONB-046) and appeal and reconsideration independence are approved. Whether one person can capture and verify the same registration is a Part F "independence" detail.

## 3. Group C: the recurring pattern

Read groups 10 to 15 of the register side by side:

| Step in the pattern | Grp 12 (195–232) | Grp 13 (233–247) | Grp 14 (248–262) | Grp 15 (263–274) |
|---|:-:|:-:|:-:|:-:|
| Controlled reopening | ✔ | ✔ | ✔ | ✔ |
| Independent approval of reopening | ✔ | ✔ | ✔ | ✔ |
| Notification | ✔ | ✔ | ✔ | ✔ |
| Failed-notification handling | ✔ | ✔ | ✔ | ✔ |
| Acknowledgement | ✔ | ✔ | ✔ | ✔ |
| Supervisory assessment and reassignment | ✔ | ✔ | ✔ | ✔ |
| Formal acceptance | ✔ | ✔ | ✔ | ✔ |
| Partial acceptance | ✔ | ✔ | ✔ | ✔ |
| Escalation of acceptance failure | ✔ | ✔ | ✔ | ✔ |
| Higher-management review | ✔ | ✔ | ✔ | ✔ |
| Historical monitoring of refusals | ✔ | ✔ | | ✔ |
| Periodic senior-management review | | ✔ | | ✔ |
| Emergency appointment and its review | | | ✔ | (subject) |
| Corrective actions and verification | ✔ | | ✔ | (subject) |

Each group applies the **same steps** to the *output of the previous group*. The subject of group 15 is "corrective actions arising from retrospective reviews of emergency management appointments for critical reassignment problem responsibilities": a corrective action, about a review, about an appointment, about a reassignment problem. Each new level adds about 12 rules that are almost identical to the previous 12.

**This pattern has no natural end.** BR-ONB-275 (pending) would start the next level.

### The fix: model it once as generic mechanisms

Each step above is a reusable mechanism that can apply to **any work item** in any module: a registration, a corrective action, a risk alert, a maintenance work order, a customer complaint.

| # | Mechanism | Covers (approximate rule ranges) | What it does |
|---|---|---|---|
| G1 | **Assignment and acceptance** | 149–151, 155–158, 199–205, 238–242, 253–258, 268–273 | Assign / reassign work; formal, partial or refused acceptance; acceptance deadline; escalate on failure; record repeated refusals |
| G2 | **Delegation and interim accountability** | 110, 143–148, 152–155 | Temporary delegation with period, extension, revocation and history; interim accountability and handover |
| G3 | **Emergency appointment and retrospective review** | 159–162, 259–261 | Appoint someone in an emergency, then independently review the appointment afterwards and classify the findings |
| G4 | **Notification and acknowledgement** | 056–057, 166–168, 196–198, 235–237, 250–252, 265–267 | Notify through authorised channels; detect and handle failed delivery; require and track acknowledgement; reminders |
| G5 | **Independent review and decision** | 046, 062, 069–070, 076–077, 089, 161, 184–189, 264 | Assign a reviewer who is independent of the matter; record findings; reach an authorised outcome; allow appeal or reopening |
| G6 | **Corrective action lifecycle** | 122–126, 163–165, 191–196, 233–234, 248–249, 262–263 | Raise, assign, complete, **independently verify effectiveness**, reopen if ineffective (with independent approval), detect recurring weaknesses |
| G7 | **Escalation and management review** | 175, 203–205, 242–247, 271–274 | Escalation ladder; higher-management review; periodic senior-management review; performance reporting |
| G8 | **Accountability suitability** | 173–190 | Periodic review of whether a responsible person is suitable; temporary restrictions; reinstatement; monitoring; appeal |
| G9 | **Incident, risk and alert register** | 111–121, 127–142 | Record incidents and risks; reconcile records after incidents; risk alerts with acknowledgement, escalation and closure; trend reporting |

With these nine mechanisms, **every level of nesting is already covered**. A corrective action about a review about an appointment is still just a G6 corrective action, assigned via G1, notified via G4, escalated via G7. No new rules are needed for the next level.

### Recommendation on pending BR-ONB-275

The *content* of BR-ONB-275 (measurable success criteria: problem, expected improvement, indicators, baseline, assessment period) is good. But approving it as a rule only for "organisational improvements arising from senior-management reviews of reassignment problems" would cover just one level of nesting.

**Recommended:** approve it as a rule of **G6 Corrective action lifecycle**, so it applies to **every** corrective action on the platform. In the domain model, those five items become fields of `CorrectiveAction`, and G6 verification checks results against them.

## 4. Group B: reclassify as platform SEC / NFR requirements

| Rules | Subject | Suggested class | Applies to |
|---|---|---|---|
| 093 – 097 | Role-based access, emergency ("break-glass") access, access revocation, periodic access reviews, monitoring of sensitive-data access | SEC | Whole platform (Identity and Access) |
| 098 – 100 | Retention and disposal; record integrity | DR / SEC | Whole platform |
| 101 – 109 | Backup, integrity, restoration, DR testing, RTO, RPO, continuity arrangements and activation | NFR | Whole platform (operations) |

These rules stay approved and keep their IDs. Reclassification adds an SEC / NFR identifier and **platform-wide scope**, so they are not re-discovered separately for BP-02 to BP-11.

## 5. Part F details become configurable policy, not new rules

The outstanding details listed in Part F of the register (authority, deadlines, evidence, notifications, independence, escalation, closure, retention, permissions, testing) are mostly **parameters of the G mechanisms**:

| Part F item | Where it lives |
|---|---|
| Authority, permissions | Identity and Access: permissions such as `registration.approve` and `correctiveAction.reopen.approve` |
| Deadlines | Policy values per work type (e.g. acceptance deadline = 2 working days) used by G1, G4 and G6 |
| Independence | One G5 independence policy (e.g. "has not captured, verified or decided this matter, and is not in the same reporting line") |
| Escalation levels | G7 escalation ladder per company / platform |
| Notifications | G4 channel list, retry schedule, failure threshold |
| Evidence and closure | Checklist definitions per work type |
| Retention | Retention classes on StoredDocument / AuditEvent |
| Testing | One acceptance-test suite per mechanism and one per use case, not one per rule |

One decision answers each item for **every** module at once.

---

## 6. What to do next

1. **Agree the three-way classification** (A core onboarding / B platform SEC+NFR / C governance mechanisms). Nothing is un-approved; rules gain a classification and a mechanism reference.
2. **Pause further nested governance rules.** Approve BR-ONB-275 as a G6 rule (see §3).
3. **Reconcile BR-ONB-012–142 titles** line by line into a master register. Suggested home: `docs/requirements/business-rules-register.md`, with the columns proposed in the register (Rule ID, BP, subprocess, title, description, status, approval date, requirements, use cases, dependencies, open details, acceptance criteria) plus two new ones: **Class** (A / B / C) and **Mechanism** (G1–G9).
4. **Resolve Part F parameters** for the first release only, mechanism by mechanism.
5. **First build slice** needs group A plus a minimal G1 (assignment), G4 (notification) and G5 (independent decision). G2, G3 and G6–G9 can follow as a separate governance module.
