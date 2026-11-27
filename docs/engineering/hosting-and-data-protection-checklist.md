# Hosting and Data Protection Checklist

**Purpose:** questions to settle with Infratel Zambia and with the Office of the Data Protection Commissioner (ODPC) or a lawyer before TransitHub holds real personal data. Basis: [ADR-0017](../adr/0017-hosting-in-zambia-infratel-keycloak.md).
**How to use:** write each answer next to its question and date it. Answers that change the design become a short ADR.

---

## Part A — Questions for Infratel

### Agreement and cost
1. What exactly does "free use" cover (compute, storage, bandwidth, support), and for how long? What happens when it ends: notice period, pricing?
2. Is there a written agreement, including a **data processing agreement** (Infratel processes data on our behalf)?
3. Who owns the data, and how do we get all of it back if we leave?

### What they provide
4. **Service model:** virtual servers only, managed PostgreSQL, containers/Kubernetes, or a mix?
5. **Server sizes available:** CPU, memory, disk. Release 1 needs roughly:
   - application: 2 vCPU, 4 GB RAM;
   - PostgreSQL: 2 vCPU, 4 GB RAM, 50 GB SSD, growing;
   - Keycloak: 2 vCPU, 2–4 GB RAM;
   - file storage for evidence: 100 GB, growing.
6. **Operating system** options and who patches it.
7. **Managed PostgreSQL:** is version 18 available? Automated backups? Point-in-time recovery?
8. **Object or file storage** for evidence photos and PDFs (S3-compatible would be ideal)?

### Reliability and recovery
9. **Uptime commitment** (e.g. 99.5%) and planned-maintenance windows.
10. **Backups:** how often, kept for how long, tested restores? Stored **in a second location inside Zambia**?
11. **Disaster recovery:** is there a second data centre in Zambia? How long to recover after a site failure?

### Network and security
12. Public IP addresses, domain names and **TLS certificates** (HTTPS).
13. Internet bandwidth and any data caps. Field tablets sync over mobile networks.
14. Firewall, DDoS protection, VPN or SSH access for administration.
15. Physical security and certifications (e.g. ISO 27001).
16. Can Infratel staff access our servers or data? Under what controls and logging?

### Support
17. Support hours, contact channels and response times, including nights and weekends.
18. Monitoring and alerting: do they offer it, or do we bring our own (must also run in Zambia)?

---

## Part B — Questions for the ODPC or a lawyer

1. **Local storage:** must all personal data (passengers, staff, company representatives) be processed **and** stored in Zambia? Are any categories exempt?
2. **Registration:** must TransitHub register as a **data controller** and/or **data processor**? What about each transport company, which holds its own passengers' data?
3. **Roles:** for passenger data, is the transport company the controller and TransitHub its processor? For onboarding data, is TransitHub the controller?
4. **Cross-border services:** may any of these process personal data outside Zambia, and on what conditions?
   - SMS gateway;
   - email delivery;
   - error monitoring;
   - offline backups;
   - support tools.
5. **Identity documents** (NRC, passport numbers) and **signatures** captured during onboarding: are extra safeguards required?
6. **Retention:** minimum and maximum periods for registrations, rejected applications, evidence, audit trails and passenger bookings.
7. **Breach notification:** who must be told, and how quickly?
8. **Data subject rights:** what access, correction and deletion requests must we support, and within what deadlines? How does this fit with an append-only audit trail?
9. **Consent and notices:** wording required for the representative declaration (onboarding) and passenger bookings.

---

## Part C — Design consequences already applied

| Area | Status |
|---|---|
| Sign-in | Keycloak self-hosted (ADR-0017); backend is provider-neutral OIDC |
| Database | PostgreSQL in Zambia; tenant isolation by row-level security (ADR-0003) |
| Audit | Append-only, in Zambia (ADR-0008). Part B #8 may need a documented policy on how correction requests are handled |
| Source code and CI | GitHub holds code and generated test data only, never real personal data |
| SMS, email, file storage, backups | **Open:** choose in-country options once Parts A and B are answered |
