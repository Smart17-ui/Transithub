package com.transithub.shared.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * The words of each notification, in plain language (no internal codes), with a
 * link back to TransitHub. {@code sms} is the short form for phones.
 */
final class NotificationMessages {

    record Message(String subject, String body, String sms) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final ZoneId ZAMBIA = ZoneId.of("Africa/Lusaka");

    private NotificationMessages() {
    }

    /**
     * @param token for an invitation only: the unsealed one-time token
     */
    static Message render(String template, Map<String, Object> p, String name, String appUrl, String token) {
        String app = appUrl.replaceAll("/+$", "");
        String reference = text(p, "reference");
        String hello = "Hello " + name + ",\n\n";
        String signOff = "\n\nTransitHub\nBus travel in Zambia, made simple.";

        return switch (template) {
            case "enquiry.received" -> new Message(
                    "New enquiry " + reference,
                    "A new enquiry (" + topic(text(p, "topic")) + ") arrived through the website.\n\n"
                            + "Open the Enquiries page to answer it: " + app + "/enquiries" + signOff,
                    "TransitHub: new enquiry " + reference + ".");
            case "registration.verification-assigned" -> new Message(
                    "Application " + reference + " is ready for you to check",
                    hello + "Application " + reference + " has been assigned to you for checking.\n\n"
                            + "Open it in TransitHub: " + app + signOff,
                    "TransitHub: application " + reference + " is assigned to you.");
            case "registration.information-requested" -> new Message(
                    "We need more information for your application " + reference,
                    hello + "To continue checking application " + reference + ", we need the following:\n\n"
                            + text(p, "description") + "\n\nPlease reply by " + day(text(p, "dueDate"))
                            + ". Sign in to answer and add any documents: " + app + signOff,
                    "TransitHub: we need more information for application " + reference + " by "
                            + day(text(p, "dueDate")) + ". Sign in: " + app);
            case "registration.information-provided" -> new Message(
                    "Information received for application " + reference,
                    hello + "The applicant has answered your request for application " + reference
                            + ". Checking can continue: " + app + signOff,
                    "TransitHub: information received for application " + reference + ".");
            case "registration.approved" -> new Message(
                    "Your application " + reference + " is approved",
                    hello + "Good news: application " + reference + " is approved. The company's TransitHub code is "
                            + text(p, "companyCode") + ".\n\nSign in to TransitHub: " + app + signOff,
                    "TransitHub: application " + reference + " is approved. Company code " + text(p, "companyCode") + ".");
            case "registration.rejected" -> new Message(
                    "Decision on your application " + reference,
                    hello + "We could not approve application " + reference + ".\n\nReasons: " + text(p, "reasons")
                            + "\n\nYou may appeal until " + day(text(p, "appealDeadline"))
                            + ". Contact our team to start an appeal, or apply again once the problem is fixed." + signOff,
                    "TransitHub: application " + reference + " was not approved. Sign in or contact us for the reasons.");
            case "company.administrator-invitation" -> {
                String link = app + "/invitation?token=" + token;
                yield new Message(
                        "Set up " + text(p, "companyName") + " on TransitHub",
                        hello + text(p, "companyName") + " is approved on TransitHub (company code "
                                + text(p, "companyCode") + "). You are invited to become its Company Administrator.\n\n"
                                + "Open this link, sign in or create your account, and accept:\n" + link
                                + "\n\nThe link works once and expires on " + day(text(p, "expiresAt")) + "." + signOff,
                        "TransitHub: set up " + text(p, "companyName") + ". Open " + link);
            }
            default -> new Message("Update from TransitHub",
                    hello + "There is an update for you in TransitHub: " + app + signOff,
                    "TransitHub: there is an update for you. " + app);
        };
    }

    private static String text(Map<String, Object> p, String key) {
        Object value = p.get(key);
        return value == null ? "" : value.toString();
    }

    /** "2026-12-05" or an instant, as "5 December 2026". */
    private static String day(String value) {
        if (value.isBlank()) {
            return "";
        }
        try {
            return value.length() == 10 ? LocalDate.parse(value).format(DAY) : Instant.parse(value).atZone(ZAMBIA).format(DAY);
        } catch (RuntimeException e) {
            return value;
        }
    }

    private static String topic(String code) {
        return switch (code) {
            case "COMPANY_REGISTRATION" -> "registering a company";
            case "TRAVEL" -> "travelling";
            case "PARTNERSHIP" -> "partnership";
            default -> "other";
        };
    }
}
