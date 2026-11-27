package com.transithub.enquiry.internal;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.enquiry.EnquiryService;
import com.transithub.shared.web.ProblemCodes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The website's Contact form ({@code /api/v1/public/enquiries}, no sign-in)
 * and the staff inbox ({@code /api/v1/enquiries}, {@code enquiry.manage}).
 */
@RestController
class EnquiryController {

    static final String TOO_MANY_REQUESTS = "too-many-requests";

    private final EnquiryService enquiries;
    private final SubmissionRateLimiter rateLimiter;

    EnquiryController(EnquiryService enquiries, SubmissionRateLimiter rateLimiter) {
        this.enquiries = enquiries;
        this.rateLimiter = rateLimiter;
    }

    /**
     * @param website a field hidden from people; automated spam fills it in.
     *                Such submissions are acknowledged but never stored.
     */
    record EnquiryRequest(
            @NotNull EnquiryService.Topic topic,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = "^\\+?[0-9 -]{7,20}$") @Nullable String phone,
            @Size(max = 200) @Nullable String companyName,
            @NotBlank @Size(min = 10, max = 4000) String message,
            @AssertTrue boolean consentGiven,
            @Nullable String website) {
    }

    record Received(@Nullable String reference) {
    }

    record EnquiryPage(List<EnquiryService.Enquiry> items, long total, int limit, int offset) {
    }

    @PostMapping("/api/v1/public/enquiries")
    ResponseEntity<?> submit(@Valid @RequestBody EnquiryRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(http.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ProblemCodes.problem(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many messages",
                    "You have sent several messages in a short time. Please try again later.", TOO_MANY_REQUESTS));
        }
        if (request.website() != null && !request.website().isBlank()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new Received(null));
        }
        String reference = EnquiryService.submitAsPlatform(enquiries, new EnquiryService.NewEnquiry(
                request.topic(), request.fullName().strip(), request.email().strip(), blankToNull(request.phone()),
                blankToNull(request.companyName()), request.message().strip(), request.consentGiven()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new Received(reference));
    }

    @GetMapping("/api/v1/enquiries")
    EnquiryPage list(@RequestParam(required = false) EnquiryService.@Nullable Status status,
                     @RequestParam(defaultValue = "50") int limit,
                     @RequestParam(defaultValue = "0") int offset) {
        int size = Math.clamp(limit, 1, 200);
        int from = Math.max(offset, 0);
        return new EnquiryPage(enquiries.list(status, size, from), enquiries.count(status), size, from);
    }

    @PostMapping("/api/v1/enquiries/{id}/resolution")
    EnquiryService.Enquiry resolve(@PathVariable UUID id) {
        return enquiries.resolve(id);
    }

    @ExceptionHandler(EnquiryService.EnquiryNotFoundException.class)
    ProblemDetail notFound(EnquiryService.EnquiryNotFoundException exception) {
        return ProblemCodes.problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), ProblemCodes.NOT_FOUND);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
