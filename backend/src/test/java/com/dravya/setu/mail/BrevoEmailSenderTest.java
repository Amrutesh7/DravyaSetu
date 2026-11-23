package com.dravya.setu.mail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class BrevoEmailSenderTest {
    private MailProperties properties() {
        var properties = new MailProperties();
        properties.setFrom("verified@example.test"); properties.setFromName("DravyaSetu");
        properties.setConnectTimeoutMs(1000); properties.setWriteTimeoutMs(1000);
        return properties;
    }

    @Test void sendsTheBrevoContractAndRequiresProviderAcceptance() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<String>(); var key = new AtomicReference<String>();
        var status = new AtomicInteger(201);
        server.createContext("/v3/smtp/email", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("api-key"));
            exchange.sendResponseHeaders(status.get(), -1); exchange.close();
        });
        server.start();
        try {
            var sender = new BrevoEmailSender(properties(), "test-api-key", "http://127.0.0.1:" + server.getAddress().getPort());
            sender.send("recipient@example.test", "Verify your email", "<p>042917</p>");
            var json = new tools.jackson.databind.ObjectMapper().readTree(body.get());
            assertEquals("test-api-key", key.get());
            assertEquals("verified@example.test", json.get("sender").get("email").asText());
            assertEquals("recipient@example.test", json.get("to").get(0).get("email").asText());
            assertEquals("<p>042917</p>", json.get("htmlContent").asText());
            status.set(200);
            assertThrows(EmailDeliveryException.class, () -> sender.send("recipient@example.test", "Subject", "Body"));
            status.set(401);
            var exception = assertThrows(EmailDeliveryException.class,
                    () -> sender.send("recipient@example.test", "Subject", "<p>042917</p>"));
            assertNull(exception.getCause());
            assertFalse(exception.getMessage().contains("test-api-key"));
            assertFalse(exception.getMessage().contains("042917"));
        } finally { server.stop(0); }
    }

    @Test void refusesMissingCredentialsAndDisabledDelivery() {
        assertThrows(EmailDeliveryException.class, () -> new BrevoEmailSender(properties(), "", "http://127.0.0.1")
                .send("recipient@example.test", "Subject", "Body"));
        var disabled = properties(); disabled.setEnabled(false);
        assertThrows(EmailDeliveryException.class, () -> new BrevoEmailSender(disabled, "key", "http://127.0.0.1")
                .send("recipient@example.test", "Subject", "Body"));
    }

    @Test void selectsExactlyOneMailProvider() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(MailProperties.class, this::properties)
                .withBean(org.springframework.mail.javamail.JavaMailSender.class,
                        () -> org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class))
                .withUserConfiguration(SmtpEmailSender.class, BrevoEmailSender.class);
        runner.run(context -> {
            assertEquals(1, context.getBeansOfType(EmailSender.class).size());
            assertInstanceOf(SmtpEmailSender.class, context.getBean(EmailSender.class));
        });
        runner.withPropertyValues("app.mail.provider=brevo", "app.mail.brevo-api-key=test-key").run(context -> {
            assertEquals(1, context.getBeansOfType(EmailSender.class).size());
            assertInstanceOf(BrevoEmailSender.class, context.getBean(EmailSender.class));
        });
    }
}
