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

## Implementation notes (27 November 2026, Release 1 step 1b)
- The runtime role is `transithub_runtime`, created once by `scripts/db/create-runtime-role.sql`. Flyway migrates as the owner `transithub_app`, while the application connects as `transithub_runtime`, which owns nothing, so row-level security always applies.
- The scope is written as two settings, `app.scope` (`platform` / `company` / empty) and `app.company_id`, by `TenantAwareDataSource` **on every connection checkout**, rather than with `SET LOCAL` per transaction. Every checkout overwrites both values, so a pooled connection never keeps a previous request's scope. The same mechanism also covers non-transactional JDBC calls.
- No scope means no tenant rows (fail closed).
- `TenantIsolationSchemaTest` fails the build if a table lacks row-level security, if the runtime role owns a table or can bypass RLS, or if the audit trail becomes changeable.
- ~~**Temporary:** until authentication (step 2), every HTTP request is treated as platform scope (`PlatformScopeUntilAuthentication`). The database layer is fully enforced and tested now; the HTTP layer starts distinguishing callers in step 2.~~ **Resolved in step 2 (27 Nov 2026):** the scope now comes from the signed-in user's role assignments (`AssignmentTenantScopeResolver`): platform roles give platform scope; a company role gives that company; anonymous, unknown or disabled users get no scope. A user in several companies chooses one with `X-Company-Id`.
