package com.transithub.onboarding.domain;

/**
 * Kinds of evidence a registration may carry.
 * [check rule text: required evidence per company type, BR-ONB-018]
 */
public enum EvidenceType {
    CERTIFICATE_OF_INCORPORATION,
    TAX_CLEARANCE,
    OPERATING_LICENCE,
    REPRESENTATIVE_IDENTITY,
    PROOF_OF_ADDRESS,
    OTHER
}
