# ADR-0017: Host in Zambia at Infratel; self-hosted Keycloak for sign-in

**Status:** Accepted (27 November 2026)
**Supersedes:** ADR-0016 entirely, and ADR-0013's choice of a South African cloud region. ADR-0013's other points (container image, environments, observability) still apply.

## Context
Zambia's Data Protection Act 2021 is understood to require personal data to be processed and stored on servers or data centres in Zambia, with cross-border transfers allowed only under prescribed conditions. AWS Cape Town (ADR-0016) is outside Zambia. Infratel Zambia has offered TransitHub free use of its in-country infrastructure. The exact legal position is being confirmed with the Office of the Data Protection Commissioner (ODPC) or a lawyer; see [../engineering/hosting-and-data-protection-checklist.md](../engineering/hosting-and-data-protection-checklist.md).

## Decision
- **Hosting:** Infratel Zambia data centre(s) for every environment that holds real personal data (staging, if it holds real data, and production). The service model (virtual servers, managed database, containers) is to be confirmed with Infratel.
- **Identity:** **Keycloak**, self-hosted on Infratel.
  - Realms: `staff` now, `passengers` at Release 3 (separate identities, ADR-0004).
  - Staff MFA: mandatory TOTP (authenticator app).
  - Sessions: 30 minutes idle, 10 hours maximum (Keycloak realm session settings).
- **Backend:** a standard OIDC resource server with no provider-specific code. It checks signature, expiry, issuer, `typ = Bearer` (access tokens only) and `azp` in an allow-list. Configuration: `OIDC_ISSUER`, `OIDC_CLIENT_IDS`, optional `OIDC_JWK_SET_URI`.
- **Other processors of personal data** (SMS, email, file storage, backups, monitoring) must run in Zambia or be confirmed lawful before use. MTN MoMo and Airtel Money (ADR-0010) are local operators.
- **Local development:** Keycloak runs without Docker (download the distribution and run `bin\kc.bat start-dev --http-port 8180`). Test data only.
- Source code and CI on GitHub are unaffected: they hold no personal data (tests use generated data only).

## Consequences
- In-country data and no hosting fees.
- **More operations work for one developer:** Keycloak, PostgreSQL, backups, patching and monitoring, unless Infratel manages some of them. This is the main risk, so automate installs and backups from the start and keep the stack small.
- No vendor lock-in for sign-in: Keycloak is open source and standard.
- A second site or off-site backup **inside Zambia** is needed for disaster recovery; to be confirmed with Infratel.
- Go-live is blocked until the ODPC/legal confirmation is recorded.
