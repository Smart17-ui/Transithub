package com.transithub.onboarding.domain;

/**
 * The declaration a representative reads before signing (AC-R1-ACK-01).
 * Every acknowledgement records the version signed, so the exact wording is
 * always provable. Changing the text means a new version; old versions stay
 * in this history.
 *
 * <p>[check rule text: approved declaration wording, BR-ONB-013–017; this
 * is a placeholder pending legal review]
 */
public record Declaration(String version, String text) {

    public static final Declaration CURRENT = new Declaration("DECL-2026-01", """
            I confirm that I am authorised to represent this company, that the information \
            and documents in this registration are true and complete to the best of my \
            knowledge, and that I consent to TransitHub processing them to assess the \
            company's registration, in accordance with the Data Protection Act, 2021 of Zambia.""");
}
