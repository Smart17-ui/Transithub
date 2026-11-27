# ADR-0007: Optimistic locking on every aggregate; idempotency keys on create and money operations

**Status:** Accepted (26 November 2026)
**Source:** R-11 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Two approvers can act at the same moment. Mobile networks cause retries and double taps, and an offline sync may resend the same data.

## Decision
- Every aggregate has a `version` column (JPA `@Version`). A conflicting second write is refused with HTTP 409 and the problem code `concurrent-modification`.
- Every endpoint that creates something or moves money accepts an `Idempotency-Key` header. Key, request hash and response are stored, and a retry replays the original response. A same key with a different body returns 422.
- Offline sync uses the record's UUID as its natural idempotency key.

## Consequences
No double decisions, no duplicate registrations, bookings or payments. There is a small storage cost for idempotency records, which are purged after a retention period.
