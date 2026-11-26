# ADR-0003: Tenant isolation in both the application and PostgreSQL row-level security

**Status:** Accepted (26 November 2026)
**Source:** R-06 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Strict tenant isolation is a core approved principle. One missed `WHERE company_id = ?` would leak one company's data to another.

## Decision
- A single shared schema. Every tenant-owned table has a non-null `company_id`.
- **Layer 1, application:** the current tenant comes from the authenticated user's scope and is applied to every query and command.
- **Layer 2, database:** PostgreSQL row-level security policies on every tenant table, using a per-transaction setting (`SET LOCAL app.company_id`). The application connects as a role that is **not** the table owner and cannot bypass RLS.
- Platform-level work (e.g. onboarding, before a company exists) uses explicitly named platform tables or a documented platform scope, never "RLS off".
- **Cross-tenant tests** are mandatory: each tenant-scoped endpoint has a test proving Company A cannot read or change Company B's data.

## Consequences
Two independent layers: a code bug alone cannot leak data. RLS adds some complexity to migrations and tests, which is accepted.
