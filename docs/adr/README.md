# Architecture Decision Records

Accepted decisions, in order. Each ADR is short: context, decision, consequences. Source: the decision log in [../engineering/recommendations.md](../engineering/recommendations.md).

| ADR | Title | From |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | R-03 |
| [0002](0002-modular-monolith-enforced-boundaries.md) | Modular monolith with automatically enforced module boundaries | R-05 |
| [0003](0003-tenant-isolation-app-and-rls.md) | Tenant isolation in both the application and PostgreSQL row-level security | R-06 |
| [0004](0004-managed-identity-authorisation-in-app.md) | Managed identity service for authentication; authorisation stays in TransitHub | R-07, R-08, R-37 |
| [0005](0005-identifiers-and-references.md) | Identifiers: device-generated UUIDv7; readable references; company code TH-ZM-nnnnnn | R-09, R-16 |
| [0006](0006-state-machines-and-commands.md) | Lifecycles as explicit state machines changed only by commands | R-10 |
| [0007](0007-optimistic-locking-and-idempotency.md) | Optimistic locking on every aggregate; idempotency keys on create and money operations | R-11 |
| [0008](0008-audit-events-and-status-history.md) | Business audit events and status history, written in the same transaction | R-12 |
| [0009](0009-offline-field-capture-single-pwa.md) | One installable web app (PWA) now, including offline field capture; device trial first | R-23, R-24, R-25 |
| [0010](0010-payments-and-merchant-of-record.md) | Payments through provider adapters; companies are merchant of record; commission invoiced | R-21, R-38, R-41 |
| [0011](0011-seat-inventory-segment-selling.md) | Seat inventory with segment selling, enforced by the database | R-20, R-42 |
| [0012](0012-delivery-workflow-solo.md) | Solo delivery workflow: direct commits to main, CI after push, local PostgreSQL | R-04 (rejected), R-30, R-31 (rejected) |
| [0013](0013-hosting-and-operations.md) | Cloud hosting in the nearest region with managed PostgreSQL | R-32, R-33 |
| [0014](0014-governance-mechanisms-shared-module.md) | Governance rules (BR-ONB-111–274) built once as shared mechanisms G1–G9 | R-19; analysis 04 |
| [0015](0015-marketplace-public-read-model.md) | Marketplace search from a public, platform-level read model | R-40 |
| [0016](0016-identity-provider-aws-cognito.md) | ~~AWS Cognito (Cape Town); AWS as the cloud~~ (superseded by 0017) | ADR-0004, ADR-0013 |
| [0017](0017-hosting-in-zambia-infratel-keycloak.md) | Host in Zambia at Infratel; self-hosted Keycloak for sign-in | Data Protection Act 2021 |
