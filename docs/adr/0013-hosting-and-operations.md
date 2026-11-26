# ADR-0013: Cloud hosting in the nearest region with managed PostgreSQL

**Status:** Accepted (26 November 2026)
**Source:** R-32, R-33 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
TransitHub holds company and passenger personal data. One developer cannot run their own database servers.

## Decision
- A major cloud provider's **South Africa region**, with **managed PostgreSQL** (automated backups and point-in-time recovery).
- The application is packaged as a container image. The first environments are `dev` (local), `staging` and `production`.
- Observability: structured JSON logs with correlation ids, Spring Boot Actuator health, and Micrometer metrics.
- **Before go-live:** confirm cross-border transfer conditions under Zambia's Data Protection Act 2021 (an action item, not yet done).

## Consequences
Low operational load. The legal check is a release blocker, not an afterthought.
