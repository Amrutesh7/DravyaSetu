package com.dravya.setu.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SMTP implementation of {@link EmailSender}.
 *
 * <p>Logging is deliberately metadata-only: recipients are not logged, and message bodies
 * (which contain OTPs) never reach a log file.</p>
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "app.mail", name = "provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipient, String subject, String htmlBody) {

        if (!properties.isEnabled()) {
            throw new EmailDeliveryException("Email delivery is disabled");
        }
        if (!StringUtils.hasText(properties.getHost())) {
            throw new EmailDeliveryException(
                    "Email delivery is not configured: set MAIL_HOST and related variables");
        }
        if (!StringUtils.hasText(properties.getFrom())) {
            throw new EmailDeliveryException(
                    "Email delivery is not configured: set MAIL_FROM");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(new InternetAddress(
                    properties.getFrom(), properties.getFromName(), "UTF-8"));
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

        } catch (MailException exception) {
            log.warn("SMTP delivery failed: {}", exception.getClass().getSimpleName());
            throw new EmailDeliveryException("The email could not be delivered", exception);

        } catch (Exception exception) {
            log.warn("Could not build the outbound email: {}",
                    exception.getClass().getSimpleName());
            throw new EmailDeliveryException("The email could not be delivered", exception);
        }
    }
}
