package com.transithub.shared.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * How messages leave TransitHub (backlog #13).
 *
 * <p><b>Email</b> goes over SMTP: Mailpit on the developer's computer; in production a
 * provider that satisfies the data-protection answers (#3), set with {@code SMTP_HOST}
 * and friends. <b>SMS</b> is logged only (number masked) until an SMS provider is chosen.
 */
@Configuration
public class MessageChannels {

    /** Sends one plain-text email. */
    public interface EmailSender {
        void send(String to, String subject, String body);
    }

    /** Sends one SMS. */
    public interface SmsSender {
        void send(String phone, String text);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender smtpEmailSender(JavaMailSender mail,
                                @Value("${transithub.notifications.from:TransitHub <no-reply@transithub.local>}") String from) {
        return (to, subject, body) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mail.send(message);
        };
    }

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    SmsSender loggingSmsSender() {
        Logger log = LoggerFactory.getLogger("transithub.sms");
        return (phone, text) -> log.info("SMS (not sent, no provider yet) to {}: {}", mask(phone), text);
    }

    /** +260971234567 becomes +260*****4567: enough to tell numbers apart in a log, no more. */
    static String mask(String phone) {
        String digits = phone.strip();
        return digits.length() <= 8 ? "****" : digits.substring(0, 4) + "*****" + digits.substring(digits.length() - 4);
    }
}
