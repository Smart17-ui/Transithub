# ADR-0014: Governance rules (BR-ONB-111–274) built once as shared mechanisms G1–G9

**Status:** Accepted (26 November 2026)
**Source:** R-19; analysis 04 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Agreed analysis: of the 263 BP-01.01 rules, ~81 are core onboarding, ~18 are platform security/NFR, and ~164 repeat one governance pattern.

## Decision
- A `governance` module provides G1–G9 (assignment and acceptance, delegation, emergency appointment, notification and acknowledgement, independent review, corrective action, escalation and management review, suitability, and incident/risk/alert), attached to any record through a `WorkItemRef (module, entityType, entityId, companyId)`.
- It is built incrementally: Release 1 needs only G1 (assignment of verification and approval work) and the basics of G4 (notifications).
- BR-ONB-275 becomes one generic G6 rule (success criteria for corrective actions). Writing further nested governance rules is paused.

## Consequences
Around 164 rules are implemented once for all 11 modules instead of per module.
