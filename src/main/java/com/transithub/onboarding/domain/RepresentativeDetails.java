package com.transithub.onboarding.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/** A company representative's details, as entered by the officer. */
public record RepresentativeDetails(
        String fullName,
        @Nullable IdDocumentType idDocumentType,
        @Nullable String idDocumentNumber,
        @Nullable String capacity,
        @Nullable String phone,
        @Nullable String email,
        boolean acknowledgementRequired,
        boolean primaryContact) {

    public enum IdDocumentType { NRC, PASSPORT, OTHER }

    public RepresentativeDetails normalised() {
        return new RepresentativeDetails(
                CompanyDetails.clean(fullName), idDocumentType, CompanyDetails.clean(idDocumentNumber),
                CompanyDetails.clean(capacity), CompanyDetails.clean(phone), CompanyDetails.clean(email),
                acknowledgementRequired, primaryContact);
    }

    Map<String, @Nullable String> asMap() {
        Map<String, @Nullable String> fields = new LinkedHashMap<>();
        fields.put("fullName", fullName);
        fields.put("idDocumentType", idDocumentType == null ? null : idDocumentType.name());
        fields.put("idDocumentNumber", idDocumentNumber);
        fields.put("capacity", capacity);
        fields.put("phone", phone);
        fields.put("email", email);
        fields.put("acknowledgementRequired", String.valueOf(acknowledgementRequired));
        fields.put("primaryContact", String.valueOf(primaryContact));
        return fields;
    }
}
