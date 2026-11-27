# TransitHub — Business Analysis Baseline

**Status:** Proposed, v0.1 (26 November 2026). For validation; not approved.

**Sources used:**
- *TransitHub Engineering Build Plan and Technical Implementation Roadmap* v1.0 (26 Nov 2026).
- *TransitHub Overall Project Progress Report* (26 Nov 2026).

- *TransitHub Consolidated Business Rules and Decisions Register* (26 Nov 2026).
- *Passenger-side business rules register* (BP-03), recorded in [../requirements/bp-03-passenger-booking-register.md](../requirements/bp-03-passenger-booking-register.md).
- *Subscription, Payments and Financial Management Register* (BP-01.04 / BP-09), recorded in [../requirements/bp-01.04-subscription-payments-register.md](../requirements/bp-01.04-subscription-payments-register.md).

**Reconciliation status:** onboarding use cases and the domain model are reconciled with the register **at rule-group level** (BR-ONB-012–274). BP-03 and BP-01.04 are reconciled with their registers at **decision level** (26 Nov 2026). The analysis is **not yet reconciled** line by line (the BR-ONB-012–142 titles are still being recovered), nor against the BP-01.02 requirement groups or the BP-01.04 logical data models. Where these documents disagree with an approved rule, **the approved rule wins**, and this baseline must be corrected.

| Document | Contents |
|---|---|
| [01-system-overview.md](01-system-overview.md) | What TransitHub does, its major parts and how they work together |
| [02-use-cases.md](02-use-cases.md) | Actors, the use-case catalogue for all 11 business processes, and detailed use cases for company onboarding |
| [03-domain-model.md](03-domain-model.md) | Domain model per module: entities, relationships, ownership, lifecycles and cross-module references |
| [04-business-rules-analysis.md](04-business-rules-analysis.md) | What the 263 BP-01.01 rules describe: a three-way classification, the recurring governance pattern, nine reusable mechanisms, and a recommendation on BR-ONB-275 |

Design (architecture detail, physical database, APIs) is deliberately out of scope here and follows once this baseline is validated.

## Conventions

- **Use case IDs:** `UC-<BP>-<nn>`, for example `UC-01.01-03` (BP-01.01, use case 3) or `UC-04-05` (BP-04, use case 5). Cross-cutting use cases use `UC-X-<area>-<nn>`.
- **Status tags:**
  - **[Approved basis]**: stated as an approved decision in the source documents.
  - **[Proposed]**: my analysis, which needs validation.
  - **[Open]**: an unresolved question that needs a business decision.
