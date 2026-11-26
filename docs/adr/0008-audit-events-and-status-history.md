# ADR-0008: Business audit events and status history, written in the same transaction

**Status:** Accepted (26 November 2026)
**Source:** R-12 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Approved rules require that important actions are auditable and that history is preserved ("who rejected us, when and why?").

## Decision
- An append-only `audit_event` table: actor, action, entity type and id, `company_id`, reason, before/after summary, timestamp and correlation id. It is written **in the same transaction** as the change.
- A per-aggregate status-history table records each transition with its actor and reason.
- The application database role has no UPDATE or DELETE rights on audit tables.
- Corrections create new records or versions. They never overwrite.

## Consequences
History cannot silently diverge from the data. Technical logs (ADR-0013) are separate and are not the audit trail.
