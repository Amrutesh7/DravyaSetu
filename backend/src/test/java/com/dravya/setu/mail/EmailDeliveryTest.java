package com.dravya.setu.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.OffsetDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailDeliveryTest {

    @Test
    void verificationTemplateCarriesTheCodeAndExpiry() {

        EmailTemplates templates = new EmailTemplates();
        EmailTemplates.EmailContent content = templates.verificationOtp("042917", 5);

        assertTrue(content.subject().toLowerCase().contains("verification"));
        assertTrue(content.html().contains("042917"));
        assertTrue(content.html().contains("5 minutes"));
        assertTrue(content.html().contains("DravyaSetu"));
        assertFalse(content.html().contains("%s"), "unrendered template placeholder");
    }

    @Test
    void resetTemplateCarriesTheCodeAndExpiry() {

        EmailTemplates templates = new EmailTemplates();
        EmailTemplates.EmailContent content = templates.passwordResetOtp("918273", 5);

        assertTrue(content.subject().toLowerCase().contains("reset"));
        assertTrue(content.html().contains("918273"));
        assertTrue(content.html().contains("5 minutes"));
        assertFalse(content.html().contains("%s"));
    }

    @Test
    void confirmationTemplateHasNoCodeAndWarnsTheUser() {

        EmailTemplates templates = new EmailTemplates();
        EmailTemplates.EmailContent content = templates.passwordChanged(OffsetDateTime.now());

        assertTrue(content.subject().toLowerCase().contains("password"));
        assertTrue(content.html().contains("signed out"));
        assertTrue(content.html().contains("did not make this change"));
    }

    @Test
    void senderRefusesToPretendWhenSmtpIsNotConfigured() {

        MailProperties properties = new MailProperties();
        properties.setHost("");
        properties.setFrom("no-reply@example.com");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, properties);

        EmailDeliveryException error = assertThrows(EmailDeliveryException.class,
                () -> sender.send("user@example.com", "Subject", "<p>Body</p>"));

        assertTrue(error.getMessage().contains("MAIL_HOST"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void senderRefusesWhenDeliveryIsDisabled() {

        MailProperties properties = new MailProperties();
        properties.setEnabled(false);
        properties.setHost("smtp.example.com");

        SmtpEmailSender sender = new SmtpEmailSender(mock(JavaMailSender.class), properties);

        assertThrows(EmailDeliveryException.class,
                () -> sender.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void senderBuildsAnHtmlMessageAndReportsProviderFailures() {

        MailProperties properties = new MailProperties();
        properties.setHost("smtp.example.com");
        properties.setFrom("no-reply@example.com");
        properties.setFromName("DravyaSetu");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(call ->
                new MimeMessage(Session.getInstance(new Properties())));

        SmtpEmailSender sender = new SmtpEmailSender(mailSender, properties);

        sender.send("user@example.com", "Subject", "<p>Body</p>");
        verify(mailSender).send(any(MimeMessage.class));

        doThrow(new MailSendException("provider down"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThrows(EmailDeliveryException.class,
                () -> sender.send("user@example.com", "Subject", "<p>Body</p>"));
    }
}
