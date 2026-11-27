package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.transithub.shared.id.UuidV7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain rules of registration drafts, without a database. */
class CompanyRegistrationTest {

    private static final Instant NOW = Instant.parse("2026-11-27T08:00:00Z");
    private static final String JOSEPH = "joseph";

    @Test
    void AC_R1_CAP_01_newDraftStartsInDraftWithAStatusHistoryEntry() {
        CompanyRegistration registration = draft(CompanyDetails.empty());

        assertEquals(RegistrationStatus.DRAFT, registration.getStatus());
        assertEquals(1, registration.getContentRevision());

        List<RegistrationHistory.StatusChange> transitions = registration.pullStatusChanges();
        assertEquals(1, transitions.size());
        assertNull(transitions.getFirst().from());
        assertEquals(RegistrationStatus.DRAFT, transitions.getFirst().to());
        assertEquals(JOSEPH, transitions.getFirst().actorId());
    }

    @Test
    void AC_R1_CAP_05_changesRecordOldAndNewValues() {
        CompanyRegistration registration = draft(details("Kafue Coaches Ltd", "+260971000001"));

        registration.updateDetails(details("Kafue Coaches Limited", "+260971000001"), JOSEPH, NOW);

        List<RegistrationHistory.FieldChange> changes = registration.pullFieldChanges();
        assertEquals(1, changes.size());
        RegistrationHistory.FieldChange change = changes.getFirst();
        assertEquals("legalName", change.field());
        assertEquals("Kafue Coaches Ltd", change.oldValue());
        assertEquals("Kafue Coaches Limited", change.newValue());
        assertTrue(change.material());
        assertEquals(JOSEPH, change.changedBy());
    }

    @Test
    void materialChangesRaiseTheContentRevisionButContactChangesDoNot() {
        CompanyRegistration registration = draft(details("Kafue Coaches Ltd", "+260971000001"));

        registration.updateDetails(details("Kafue Coaches Ltd", "+260971000002"), JOSEPH, NOW);
        assertEquals(1, registration.getContentRevision(), "a phone change is not material");

        registration.updateDetails(details("Kafue Coaches Limited", "+260971000002"), JOSEPH, NOW);
        assertEquals(2, registration.getContentRevision(), "a legal-name change is material");
    }

    @Test
    void blanksAndWhitespaceAreNotChanges() {
        CompanyRegistration registration = draft(details("Kafue Coaches Ltd", null));

        registration.updateDetails(details("  Kafue Coaches Ltd  ", "   "), JOSEPH, NOW);

        assertTrue(registration.pullFieldChanges().isEmpty());
        assertEquals(1, registration.getContentRevision());
    }

    @Test
    void onlyOneRepresentativeIsThePrimaryContact() {
        CompanyRegistration registration = draft(CompanyDetails.empty());

        Representative banda = registration.addRepresentative(UuidV7.generate(), representative("Mr Banda", true), JOSEPH, NOW);
        Representative phiri = registration.addRepresentative(UuidV7.generate(), representative("Mrs Phiri", true), JOSEPH, NOW);

        assertFalse(banda.isPrimaryContact());
        assertTrue(phiri.isPrimaryContact());
        assertEquals(2, phiri.getPosition());
    }

    @Test
    void losingPrimaryContactIsRecordedInTheHistory() {
        CompanyRegistration registration = draft(CompanyDetails.empty());
        Representative banda = registration.addRepresentative(UuidV7.generate(), representative("Mr Banda", true), JOSEPH, NOW);
        registration.pullFieldChanges();

        registration.addRepresentative(UuidV7.generate(), representative("Mrs Phiri", true), JOSEPH, NOW);

        assertTrue(registration.pullFieldChanges().stream().anyMatch(change ->
                change.field().equals("representative[" + banda.getId() + "].primaryContact")
                        && "true".equals(change.oldValue()) && "false".equals(change.newValue())));
    }

    @Test
    void representativeChangesAreMaterial() {
        CompanyRegistration registration = draft(CompanyDetails.empty());
        Representative banda = registration.addRepresentative(UuidV7.generate(), representative("Mr Banda", true), JOSEPH, NOW);
        assertEquals(2, registration.getContentRevision());

        registration.removeRepresentative(banda.getId(), JOSEPH, NOW);

        assertEquals(3, registration.getContentRevision());
        assertTrue(registration.activeRepresentatives().isEmpty());
    }

    @Test
    void AC_R1_CAP_03_incompleteDraftListsEverythingMissing() {
        List<String> missing = draft(CompanyDetails.empty()).missingForSubmission();

        assertTrue(missing.containsAll(List.of(
                "company.legalName", "company.registrationNumber", "company.operatingLicenceNumber",
                "company.businessType", "company.district", "company.physicalAddress", "company.contactPhone",
                "representatives.atLeastOne", "evidence.certificateOfIncorporation", "evidence.operatingLicence")));
    }

    @Test
    void AC_R1_CAP_03_completeDraftHasNothingMissing() {
        CompanyRegistration registration = draft(new CompanyDetails(
                "Kafue Coaches Ltd", null, "120230045678", "RSL-2026-0042", "Bus operator", "Lusaka", "Kafue",
                "Plot 12, Kafue Road", null, null, "+260971000001"));
        Representative banda = registration.addRepresentative(UuidV7.generate(), new RepresentativeDetails(
                "Mr Banda", RepresentativeDetails.IdDocumentType.NRC, "123456/10/1", "Director",
                "+260971000002", null, true, true), JOSEPH, NOW);
        registration.attachEvidence(UuidV7.generate(), EvidenceType.CERTIFICATE_OF_INCORPORATION, file(), JOSEPH, NOW);
        registration.attachEvidence(UuidV7.generate(), EvidenceType.OPERATING_LICENCE, file(), JOSEPH, NOW);
        registration.acknowledge(UuidV7.generate(), banda.getId(), UuidV7.generate().toString(), "sha",
                Declaration.CURRENT.version(), JOSEPH, NOW);

        assertEquals(List.of(), registration.missingForSubmission());
    }

    @Test
    void AC_R1_ACK_01_acknowledgementAppliesToTheCurrentContentRevision() {
        CompanyRegistration registration = draft(CompanyDetails.empty());
        Representative banda = registration.addRepresentative(UuidV7.generate(), representative("Mr Banda", true), JOSEPH, NOW);

        Acknowledgement ack = registration.acknowledge(UuidV7.generate(), banda.getId(), UuidV7.generate().toString(),
                "sha", Declaration.CURRENT.version(), JOSEPH, NOW);

        assertEquals(registration.getContentRevision(), ack.getContentRevision());
        assertEquals(Acknowledgement.Outcome.ACKNOWLEDGED, registration.currentResponseOf(banda.getId()).orElseThrow().getOutcome());
    }

    @Test
    void AC_R1_ACK_02_refusalBlocksSubmission() {
        CompanyRegistration registration = draft(CompanyDetails.empty());
        Representative banda = registration.addRepresentative(UuidV7.generate(), representative("Mr Banda", true), JOSEPH, NOW);

        registration.refuse(UuidV7.generate(), banda.getId(), "Does not agree with the declared fleet size", JOSEPH, NOW);

        assertTrue(registration.missingForSubmission().contains("representative[" + banda.getId() + "].refused"));
    }

    @Test
    void AC_R1_ACK_03_materialChangeInvalidatesEverySignature() {
        CompanyRegistration registration = draft(details("Kafue Coaches Ltd", null));
        Representative banda = registration.addRepresentative(UuidV7.generate(), representative("Mr Banda", true), JOSEPH, NOW);
        registration.acknowledge(UuidV7.generate(), banda.getId(), UuidV7.generate().toString(), "sha",
                Declaration.CURRENT.version(), JOSEPH, NOW);

        registration.updateDetails(details("Kafue Coaches Ltd", "+260971000009"), JOSEPH, NOW);
        assertTrue(registration.currentResponseOf(banda.getId()).isPresent(), "a phone change keeps the signature");

        registration.attachEvidence(UuidV7.generate(), EvidenceType.TAX_CLEARANCE, file(), JOSEPH, NOW);
        assertTrue(registration.currentResponseOf(banda.getId()).isEmpty(), "new evidence needs a new signature");
    }

    @Test
    void anewResponseReplacesTheRepresentativesEarlierOne() {
        CompanyRegistration registration = draft(CompanyDetails.empty());
        Representative banda = registration.addRepresentative(UuidV7.generate(), representative("Mr Banda", true), JOSEPH, NOW);
        registration.refuse(UuidV7.generate(), banda.getId(), "Not yet", JOSEPH, NOW);

        registration.acknowledge(UuidV7.generate(), banda.getId(), UuidV7.generate().toString(), "sha",
                Declaration.CURRENT.version(), JOSEPH, NOW);

        assertEquals(Acknowledgement.Outcome.ACKNOWLEDGED,
                registration.currentResponseOf(banda.getId()).orElseThrow().getOutcome());
    }

    private static Evidence.StoredFile file() {
        return new Evidence.StoredFile("certificate.pdf", "application/pdf", 1234, "sha", UuidV7.generate().toString());
    }

    private static CompanyRegistration draft(CompanyDetails details) {
        return CompanyRegistration.startDraft(UuidV7.generate(), "REG-2026-000001",
                RegistrationChannel.FIELD, details, JOSEPH, NOW);
    }

    private static CompanyDetails details(String legalName, String phone) {
        return new CompanyDetails(legalName, null, null, null, null, null, null, null, null, null, phone);
    }

    private static RepresentativeDetails representative(String name, boolean primary) {
        return new RepresentativeDetails(name, null, null, null, null, null, true, primary);
    }
}
