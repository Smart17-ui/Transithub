
package com.transithub.company.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(

        @NotBlank
        @Size(max = 20)
        String companyCode,

        @NotBlank
        @Size(max = 200)
        String legalName,

        @Size(max = 200)
        String tradingName,

        @Size(max = 100)
        String registrationNumber,

        @Email
        @Size(max = 254)
        String contactEmail,

        @Size(max = 30)
        String contactPhone

) {
}
