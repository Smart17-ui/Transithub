
package com.transithub.onboarding.domain.exception;

public class CompanyAlreadyExistsException extends RuntimeException {

    public CompanyAlreadyExistsException(String companyCode) {
        super("A company with code '" + companyCode + "' already exists.");
    }
}
