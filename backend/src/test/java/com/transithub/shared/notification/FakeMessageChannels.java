package com.transithub.shared.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Captures emails and SMS in tests instead of sending them. */
@Configuration
public class FakeMessageChannels {

    public record Sent(String to, String subject, String body) {
    }

    public static final List<Sent> EMAILS = new CopyOnWriteArrayList<>();
    public static final List<Sent> SMS = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    MessageChannels.EmailSender capturingEmailSender() {
        return (to, subject, body) -> EMAILS.add(new Sent(to, subject, body));
    }

    @Bean
    @Primary
    MessageChannels.SmsSender capturingSmsSender() {
        return (phone, text) -> SMS.add(new Sent(phone, "", text));
    }
}
