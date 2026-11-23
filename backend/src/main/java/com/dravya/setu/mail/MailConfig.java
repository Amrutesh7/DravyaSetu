package com.dravya.setu.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /**
     * Builds the mail sender explicitly instead of relying on Spring Boot's
     * {@code spring.mail.*} auto-configuration, so the application always starts and the
     * failure mode is a clear {@code 503} rather than a missing bean.
     */
    @Bean
    public JavaMailSender javaMailSender(MailProperties properties) {

        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        sender.setHost(StringUtils.hasText(properties.getHost())
                ? properties.getHost()
                : "localhost");
        sender.setPort(properties.getPort());
        sender.setDefaultEncoding("UTF-8");

        if (StringUtils.hasText(properties.getUsername())) {
            sender.setUsername(properties.getUsername());
            sender.setPassword(properties.getPassword());
        }

        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.put("mail.transport.protocol", "smtp");
        mailProperties.put("mail.smtp.auth",
                String.valueOf(StringUtils.hasText(properties.getUsername())));
        mailProperties.put("mail.smtp.starttls.enable",
                String.valueOf(properties.isStarttls()));
        mailProperties.put("mail.smtp.ssl.enable", String.valueOf(properties.isSsl()));
        mailProperties.put("mail.smtp.connectiontimeout",
                String.valueOf(properties.getConnectTimeoutMs()));
        mailProperties.put("mail.smtp.timeout",
                String.valueOf(properties.getWriteTimeoutMs()));
        mailProperties.put("mail.smtp.writetimeout",
                String.valueOf(properties.getWriteTimeoutMs()));

        return sender;
    }
}
