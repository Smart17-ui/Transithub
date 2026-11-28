# BP-03 Passenger Booking and Ticketing: Business Rules Register

**Source:** *TransitHub — Passenger-side business rules register* (restored 26 November 2026 from discussions on 23 November 2026).
**Status of this file:** a faithful record of the source, not analysis. Where analysis documents disagree with an **Approved** item here, this register wins.

**Identifiers:** the original rule IDs could not be recovered. Items are labelled by section (A–I) **as working references only**. They are not official BR IDs. Official IDs (proposed format `BR-BKG-nnn`) are assigned when each rule is formally confirmed. Do not back-date them.

---

## 1. Subprocess structure (accepted working structure)

The reference labels were reconstructed and have not been verified as the original official IDs.

| Reference | Subprocess | Main business-rule areas |
|---|---|---|
| BP-03.01 | Passenger Account Management | Registration, authentication, passenger profiles, account security |
| BP-03.02 | Journey Search and Availability Checking | Search criteria, journey information, trip availability, fares |
| BP-03.03 | Seat Selection and Reservation | Seat selection, availability, reservations, seat protection |
| BP-03.04 | Online Ticket Sales | Online purchases and payment confirmation |
| BP-03.05 | Physical Ticket Sales | Sales through company offices and authorised physical agents |
| BP-03.06 | Ticket Issuance and Validation | Ticket generation, authenticity, validation |
| BP-03.07 | Booking Modification and Cancellation | Changes, cancellations, company policies |
| BP-03.08 | Ticket Refund Management | Refund eligibility, processing, financial settlement |
| BP-03.09 | Ticket Sales Reconciliation | Sales records, transaction matching, financial reconciliation |
| BP-03.10 | Ticket Sales Reporting | Sales performance, channel reporting, management information |

## 2. Approved decisions

### A. Passenger and employee identities are separate (BP-03.01) — Approved
- BP-01 manages company employees and company-user records. BP-03 manages passenger accounts.
- Passenger registration and authentication belong to BP-03.
- Employee accounts and passenger accounts are separate. Creating one never automatically creates the other.
- Access to one never automatically grants access to the other.
- Passenger information is protected through appropriate privacy and access controls.

### B. Shared availability across sales channels (BP-03.02) — Approved
- An online passenger and a company agent searching the same journey must see consistent availability.
- The online marketplace and physical channels must not have separate, conflicting seat availability. This prevents double-selling.

### C. Seat exclusivity across channels; reservation is not purchase (BP-03.03) — Approved
- A seat sold online becomes unavailable to physical channels, and vice versa.
- All channels use the same authoritative trip and seat availability.
- Selecting or reserving a seat does not mean the ticket is purchased. **Reservation and confirmed purchase are different business states.**

### D. Payment confirmed before ticket issuance (BP-03.04) — Approved
- Starting a payment is not proof of payment.
- A booking is not marked paid because a payment was started.
- Payment must be confirmed before the booking is treated as paid.
- Tickets are issued only after the required payment confirmation and purchase conditions.

### E. Physical agents must use TransitHub (BP-03.05) — Approved
- Authorised physical sales agents sell tickets and record transactions in TransitHub.
- Physical and online channels share trip and seat availability.
- The sales channel of every ticket is retained.

### F. Controlled ticket issuance (BP-03.06) — Approved
- A ticket is not issued as a completed purchase before the required payment or purchase confirmation.
- Ticket records remain traceable to their booking and sales transaction.
- *Detailed validation, boarding verification, duplicate-use prevention and offline validation rules still need discovery (see §5).*

### G. Company policies govern changes and cancellations (BP-03.07) — Approved
- Changes and cancellations follow the transport company's approved policies.
- A modification or cancellation is distinct from a financial refund or settlement.
- Changing or cancelling a booking never silently erases the original transaction history.

### H. Refund workflow separate from financial approval and settlement (BP-03.08) — Approved
- BP-03 owns the ticket and booking cancellation and refund **workflow**.
- BP-09 owns financial **approval**, accounting and settlement.
- A cancellation request does not mean money has been refunded.
- Financial refund processing is traceable to the original transaction.

### I. Ticket sales are traceable (BP-03.09, BP-03.10) — Approved
- Successful sales have traceable financial records.
- Online sales and physical-agent sales are distinguished.
- Original booking and ticket history is preserved through later cancellations, refunds and adjustments.

### J. Guest and passenger-account access (BP-03.01) — Approved 27 Nov 2026
- **Guests may book without an account.** They give a name and phone number; the ticket is sent by SMS.
- **A guest returns to a booking with its reference and phone number, confirmed by a one-time SMS code** sent to that number. Only the person holding the phone can change or cancel it.
- **Passenger accounts use phone number + one-time SMS code** (no password). Email may be added later for receipts.
- **Guest bookings are linked to a new account automatically** once the passenger proves ownership of the same phone number with an SMS code.
- Passengers sign in from the booking pages. The site header's "Sign in" is for TransitHub staff and transport-company users (decision A keeps the two account types separate).
- *Not yet numbered rules; to be confirmed when BP-03 rule IDs are assigned. SMS provider must satisfy the data-protection answers (ADR-0017).*

### K. Payouts only to the operator's own verified account — Approved 27 Nov 2026
- Ticket money from marketplace sales is paid only to a mobile-money or bank account **in the company's name or a director's name**, verified before the company may sell through the marketplace.
- Purpose: a fraudulent operator cannot divert passengers' money to an untraceable account. Settlement itself belongs to BP-09.

### L. Multi-channel ticket delivery — Product owner direction, 27 Nov 2026
- The ticket is issued **immediately after payment is confirmed** (consistent with decisions D and F).
- Delivery channels, any combination of which the passenger may choose where the company supports them:
  1. **Digital ticket** in the passenger account, or through a secure booking-confirmation link (guests).
  2. **Email**: the ticket, or a secure retrieval link, when an email address is provided.
  3. **SMS**: confirmation with the ticket reference and a secure retrieval link where supported. SMS must not expose sensitive booking details.
  4. **Printed ticket** at physical booking offices immediately after purchase.
- Passengers can **download, print or retrieve** their ticket later; no passenger depends on a single channel.
- *To be confirmed as numbered rules with the BP-03 rule IDs; SMS and email providers must satisfy the data-protection answers (ADR-0017).*

## 3. Ownership boundary: BP-03 and BP-09 — Approved

| BP-03 Passenger Booking and Ticketing | BP-09 Financial Management |
|---|---|
| Passenger accounts | Financial transaction records |
| Journey search | Payment accounting |
| Seat reservations | Revenue recognition and recording |
| Bookings and ticket purchases | Financial reconciliation |
| Ticket issuance and validation | Refund approval and settlement |
| Booking changes and cancellations | Commission accounting |
| Ticket refund requests and workflow | Financial reporting |

## 4. Marketplace commission — Approved in principle
- TransitHub earns subscription revenue from subscribed companies.
- TransitHub may earn commission on successful marketplace ticket purchases.
- Physical ticket-office sales do not automatically attract marketplace commission.
- **Open:** commission rates, calculation, settlement and refund treatment.

## 5. Topics awaiting discovery (not approved)

| No. | Area | What needs deciding |
|---|---|---|
| 1 | Reservation expiry | When does an unpaid reservation expire? |
| 2 | Temporary seat holding | How long can a seat stay held during checkout? |
| 3 | Failed payments | What happens when payment fails, times out or has an uncertain result? |
| 4 | Duplicate bookings | How are duplicate bookings for the same passenger and journey prevented? |
| 5 | Ticket transfers | Can tickets be transferred to another person, and on what conditions? |
| 6 | Refund eligibility | What makes a ticket eligible for a refund? |
| 7 | Refund approval | Who may approve a refund, and what limits apply? |
| 8 | Offline physical sales | How do agents operate without connectivity? |
| 9 | Passenger identity verification | When must passenger identity be checked? |
| 10 | Boarding validation | How is a ticket checked before boarding? |
| 11 | Ticket reuse prevention | How is a ticket prevented from being used more than once? |
| 12 | Passenger account recovery | How are lost accounts and forgotten credentials recovered? |
| 13 | Passenger booking history | What history can passengers see? |
| 14 | Journey changes | What happens when the company changes or cancels a scheduled trip? |
| 15 | Seat reassignment | What happens when a passenger's assigned seat becomes unavailable? |

## 6. Passenger journey across processes — Approved boundaries
1. **BP-03:** search, reserve, purchase, confirmation.
2. **BP-04:** the actual trip, passenger manifest, boarding and journey execution.
3. **BP-07:** luggage registration, allowance checks, charges, association with the passenger.
4. **BP-10:** complaints, enquiries and formal cases.

- BP-07 owns authoritative luggage acceptance, registration, allowances, charges, tags and luggage claims. BP-04 handles loading, transport, unloading and handovers.
- BP-08 owns independent parcel booking and delivery. Accompanied luggage and parcels are never the same workflow.

## 7. Status summary

| Work | Status |
|---|---|
| Ten subprocess areas | Accepted working structure |
| Passenger/employee account separation | Approved |
| Shared online and physical availability | Approved |
| Cross-channel seat exclusivity | Approved |
| Reservation versus confirmed purchase | Approved |
| Payment confirmation before ticket issuance | Approved |
| Company policies for modifications and cancellations | Approved |
| BP-03/BP-09 financial ownership boundary | Approved |
| Sales-channel distinction and transaction traceability | Approved |
| Reservation expiry and failed-payment rules | Further discovery |
| Transfers and duplicate bookings | Further discovery |
| Refund eligibility and approval details | Further discovery |
| Offline ticket sales and boarding validation | Further discovery |

**Note from the source:** the 263 approved rules (BR-ONB-012–274) are the BP-01.01 sequence only. They are not the total number of TransitHub rules and include no passenger rules.
