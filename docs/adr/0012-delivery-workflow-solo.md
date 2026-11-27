# ADR-0012: Solo delivery workflow: direct commits to main, CI after push, local PostgreSQL

**Status:** Accepted (26 November 2026)
**Source:** R-04 (rejected), R-30, R-31 (rejected) in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
One developer, with no second reviewer and no Docker.

## Decision
- Commits go **directly to `main`**. GitHub Actions CI runs on every push.
- Safeguards: run `./mvnw verify` locally before pushing, and **a red CI build is fixed before any other work**.
- Tests run against the locally installed PostgreSQL (`transithub_test`), with `DB_PASSWORD` as a persistent user environment variable. CI uses a PostgreSQL service container.
- Test names cite the rule or use case they prove (e.g. `BR_ONB_046_verifierCannotApproveOwnVerification`).
- Revisit pull-request review when a second developer joins.

## Consequences
Fast and simple. Mistakes can reach `main` before CI catches them, which is accepted while the team is one person.
