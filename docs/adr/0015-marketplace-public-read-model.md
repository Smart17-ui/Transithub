# ADR-0015: Marketplace search from a public, platform-level read model

**Status:** Accepted (26 November 2026)
**Source:** R-40 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
The marketplace shows many companies' trips together, which collides with tenant isolation.

## Decision
- Companies **publish** trips, fares and availability as events into a platform-owned `marketplace` read model, which holds public data only.
- Public search never queries tenant tables.
- Each marketplace booking is still a company booking in BP-03 under its `company_id`. TransitHub's marketplace order holds the channel and the commission reference.

## Consequences
Tenant isolation is preserved and search scales independently. The read model is eventually consistent, so availability is re-checked against the authoritative inventory when the passenger reserves.
