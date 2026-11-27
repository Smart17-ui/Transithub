package com.transithub.onboarding.domain.exception;

/**
 * No decision before an independent reviewer has endorsed the
 * recommendation (BR-ONB-062, AC-R1-DEC-01). HTTP 409.
 */
public class RecommendationNotEndorsedException extends RuntimeException {

    public RecommendationNotEndorsedException() {
        super("The recommendation has not been endorsed by an independent reviewer yet.");
    }
}
