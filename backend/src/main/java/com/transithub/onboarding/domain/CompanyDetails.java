package com.transithub.onboarding.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The company's details as captured on a registration. Every field may be
 * empty while the registration is a draft (AC-R1-CAP-03: an incomplete
 * draft is saved, and submission lists what is missing).
 */
@Embeddable
public record CompanyDetails(
        @Column(name = "legal_name") @Nullable String legalName,
        @Column(name = "trading_name") @Nullable String tradingName,
        @Column(name = "registration_number") @Nullable String registrationNumber,
        @Column(name = "business_type") @Nullable String businessType,
        @Column(name = "province") @Nullable String province,
        @Column(name = "district") @Nullable String district,
        @Column(name = "physical_address") @Nullable String physicalAddress,
        @Column(name = "postal_address") @Nullable String postalAddress,
        @Column(name = "contact_email") @Nullable String contactEmail,
        @Column(name = "contact_phone") @Nullable String contactPhone) {

    /**
     * Changes to these fields alter the company's identity, so
     * representatives must acknowledge again (AC-R1-ACK-03).
     * [check rule text: definition of material change, BR-ONB-013–017]
     */
    static final Set<String> MATERIAL_FIELDS =
            Set.of("legalName", "tradingName", "registrationNumber", "businessType");

    public static CompanyDetails empty() {
        return new CompanyDetails(null, null, null, null, null, null, null, null, null, null);
    }

    /** Trims values and turns blanks into {@code null}, so "" and null never differ. */
    public CompanyDetails normalised() {
        return new CompanyDetails(clean(legalName), clean(tradingName), clean(registrationNumber),
                clean(businessType), clean(province), clean(district), clean(physicalAddress),
                clean(postalAddress), clean(contactEmail), clean(contactPhone));
    }

    /** Field name → value, in a stable order, for change tracking. */
    Map<String, @Nullable String> asMap() {
        Map<String, @Nullable String> fields = new LinkedHashMap<>();
        fields.put("legalName", legalName);
        fields.put("tradingName", tradingName);
        fields.put("registrationNumber", registrationNumber);
        fields.put("businessType", businessType);
        fields.put("province", province);
        fields.put("district", district);
        fields.put("physicalAddress", physicalAddress);
        fields.put("postalAddress", postalAddress);
        fields.put("contactEmail", contactEmail);
        fields.put("contactPhone", contactPhone);
        return fields;
    }

    static @Nullable String clean(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
