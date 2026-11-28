package com.transithub.onboarding.repository;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.transithub.identity.AccountUsage;

/**
 * An account that has ever had an application, whatever its outcome, is kept: the
 * application is a business record and its applicant must stay identifiable.
 */
@Component
class ApplicantAccountUsage implements AccountUsage {

    private final JdbcClient jdbc;

    ApplicantAccountUsage(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean inUse(UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM company_registration WHERE applicant_user_id = :id)")
                .param("id", userId)
                .query(Boolean.class).single();
    }
}
