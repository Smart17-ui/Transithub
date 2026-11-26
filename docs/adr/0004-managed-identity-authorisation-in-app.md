# ADR-0004: Managed identity service for authentication; authorisation stays in TransitHub

**Status:** Accepted (26 November 2026)
**Source:** R-07, R-08, R-37 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
TransitHub has one developer, who cannot be on call for a self-hosted login server. Staff and passengers both need accounts, and approved rules require them to be **separate**. Approval rules (verifier ≠ approver, preparer ≠ approver) are business rules, not login rules.

## Decision
- **Authentication** (sign-in, MFA, password reset, lockout) uses a **managed identity service**. The provider is chosen in a follow-up ADR; candidates are Auth0, AWS Cognito and Zitadel Cloud. Selection criteria: price at expected user numbers, a region near Zambia, SMS/OTP support, and standard OIDC.
- **Two separate user pools**, one for staff (platform and company) and one for passengers. A passenger identity can never obtain staff access.
- The backend is an OAuth2 resource server validating tokens. It holds no passwords.
- **Authorisation** lives in TransitHub: fine-grained permissions (e.g. `registration.approve`) granted through roles with a **scope** (platform, company or branch). Job titles and positions grant nothing.
- **Separation of duties** is checked against the actual record in the application layer (e.g. the approver of a registration must not be its verifier or capturer).

## Consequences
No identity server to operate. Business rules on authority stay testable in our own code. There is a monthly cost and some vendor dependence, reduced by sticking to standard OIDC.
