package com.dravya.setu.mail;

/**
 * Delivers a transactional email. Implementations must throw {@link EmailDeliveryException}
 * when the message could not be handed to the provider; callers rely on that to avoid
 * treating an account as verified or a reset as requested when nothing was sent.
 */
public interface EmailSender {

    void send(String recipient, String subject, String htmlBody);
}
