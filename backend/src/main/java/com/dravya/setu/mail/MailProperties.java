package com.dravya.setu.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMTP settings. Every value comes from the environment; nothing is hardcoded.
 *
 * <p>When {@code app.mail.host} is blank the application still starts, but every send fails
 * with {@link EmailDeliveryException}. That is deliberate: a missing mail configuration must
 * never silently pretend that a verification code was delivered.</p>
 */
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private boolean enabled = true;
    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "";
    private String fromName = "DravyaSetu";
    private boolean starttls = true;
    private boolean ssl = false;
    private String appBaseUrl = "http://localhost:8080";
    private long connectTimeoutMs = 10_000;
    private long writeTimeoutMs = 10_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getWriteTimeoutMs() {
        return writeTimeoutMs;
    }

    public void setWriteTimeoutMs(long writeTimeoutMs) {
        this.writeTimeoutMs = writeTimeoutMs;
    }
}
