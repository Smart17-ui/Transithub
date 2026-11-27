# ADR-0016: AWS Cognito (Cape Town) as the managed identity service; AWS as the cloud

**Status:** Superseded by [ADR-0017](0017-hosting-in-zambia-infratel-keycloak.md) (27 November 2026): personal data must stay in Zambia.
**Follows:** ADR-0004 (managed identity, authorisation in TransitHub) and ADR-0013 (cloud hosting)

## Context
ADR-0004 chose a managed login service but not which one. The cloud provider was also open. One developer benefits from a single provider: one account, one bill, and data kept in one region.

## Decision
- **Cloud:** AWS, region **af-south-1 (Cape Town)**, with managed PostgreSQL (RDS) for staging and production.
- **Identity:** **Amazon Cognito user pools** in the same region.
  - Two pools (ADR-0004): **staff** (platform and company users) and, at Release 3, **passengers**.
  - The backend is an OAuth2 resource server. It validates Cognito **access tokens**: signature (JWKS), issuer, `token_use = access`, and `client_id` from an allow-list.
  - A TransitHub user is linked to Cognito by the token's `sub`. TransitHub stores no passwords.
  - Roles, permissions, scope and separation of duties stay in TransitHub's database (ADR-0004). Cognito groups are **not** used for authorisation.
- **Staff MFA:** mandatory **authenticator app (TOTP)** for all staff. SMS MFA is not used, because of its cost and SIM-swap risk.
- **Sessions:** signed out after **30 minutes idle** and **10 hours maximum**. Cognito access tokens last 15 minutes, and refresh tokens are limited to 10 hours. The 30-minute idle rule is enforced by the app (frontend and session layer), because Cognito has no idle timeout.

## Consequences
- Low cost at Release 1 volumes (within Cognito's free tier). Staff and passenger identity data stays in-region.
- **Exit cost:** Cognito does not export password hashes, so moving away later means users set new passwords. This is accepted. Keeping to standard OIDC limits the code changes.
- **To verify when creating the pools:** Cognito feature availability and current pricing in af-south-1. If a required feature is missing there, record a superseding ADR before choosing another region (the Data Protection Act cross-border check in ADR-0013 applies).
- Tests never call Cognito: they use signed test tokens or Spring Security's JWT test support.
