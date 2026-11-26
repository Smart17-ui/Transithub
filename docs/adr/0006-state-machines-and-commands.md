# ADR-0006: Lifecycles as explicit state machines changed only by commands

**Status:** Accepted (26 November 2026)
**Source:** R-10 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Most approved rules have the form "X may happen only when Y". A generic "update status" operation would let any screen skip verification.

## Decision
- Each aggregate (registration, company, later booking, trip…) has a status enum and **domain methods** such as `submit()`, `startVerification()`, `recommend()`, `approve()`, `reject()`.
- Each method checks the allowed transition and preconditions, records a status-history row, and raises a domain event.
- The API exposes commands as sub-resources (e.g. `POST /registrations/{id}/approve`). There is no endpoint that sets a status field.
- The server returns `allowedActions` per record, so the UI shows only valid actions (R-26).

## Consequences
Rules live in one place and are unit-testable. Every transition is audited by construction.
