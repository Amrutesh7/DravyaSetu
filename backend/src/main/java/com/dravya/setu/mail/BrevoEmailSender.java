package com.dravya.setu.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Map;

/** HTTPS mail delivery for hosts that block SMTP. No request body/key is logged. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {
    private final MailProperties properties;
    private final String apiKey;
    private final RestClient client;

    public BrevoEmailSender(MailProperties properties,
            @Value("${app.mail.brevo-api-key:}") String apiKey,
            @Value("${app.mail.brevo-base-url:https://api.brevo.com}") String baseUrl) {
        this.properties = properties;
        this.apiKey = apiKey;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeoutMs());
        factory.setReadTimeout((int) properties.getWriteTimeoutMs());
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override public void send(String recipient, String subject, String htmlBody) {
        if (!properties.isEnabled() || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(properties.getFrom())) {
            throw new EmailDeliveryException("Brevo delivery requires enabled mail, BREVO_API_KEY and MAIL_FROM");
        }
        try {
            var response = client.post().uri("/v3/smtp/email")
                    .header("api-key", apiKey).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("sender", Map.of("email", properties.getFrom(), "name", properties.getFromName()),
                            "to", List.of(Map.of("email", recipient)), "subject", subject, "htmlContent", htmlBody))
                    .retrieve().toBodilessEntity();
            if (response.getStatusCode().value() != 201) {
                throw new EmailDeliveryException("Email provider did not confirm acceptance");
            }
        } catch (RestClientException exception) {
            // Don't attach the HTTP exception: its body might echo an OTP, address or API key.
            throw new EmailDeliveryException("The email provider could not accept the message");
        }
    }
}
