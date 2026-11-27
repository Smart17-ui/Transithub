package com.transithub.onboarding.domain;

import java.util.List;
import java.util.UUID;

/**
 * The editable content of a draft at one moment: company details and active
 * representatives. Used to sync offline edits, and to keep both versions
 * side by side when they conflict (AC-R1-SYN-05). Evidence and signatures
 * are uploaded separately, through their own endpoints.
 */
public record DraftSnapshot(CompanyDetails details, List<RepresentativeSnapshot> representatives) {

    public DraftSnapshot {
        details = details == null ? CompanyDetails.empty() : details;
        representatives = representatives == null ? List.of() : List.copyOf(representatives);
    }

    /** A representative with the id the device generated for them. */
    public record RepresentativeSnapshot(UUID id, RepresentativeDetails details) {
    }
}
