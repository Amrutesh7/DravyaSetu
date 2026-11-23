package com.dravya.setu.mail;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders the transactional emails as self-contained HTML (inline styles, no remote assets),
 * so they render consistently in Gmail, Outlook and mobile clients.
 */
@Component
public class EmailTemplates {

    private static final String BRAND = "DravyaSetu";
    private static final String BRAND_COLOR = "#1f6f43";
    private static final String TEXT_COLOR = "#1f2933";
    private static final String MUTED_COLOR = "#616e7c";
    private static final String BORDER_COLOR = "#e4e7eb";

    public record EmailContent(String subject, String html) { }

    public EmailContent verificationOtp(String code, long expiryMinutes) {

        String body = """
                <p style="margin:0 0 16px;">Welcome to %s. Use the code below to confirm this
                email address and activate your account.</p>
                %s
                %s
                <p style="margin:0 0 16px;">Enter this code on the verification screen. It expires
                in <strong>%d minutes</strong>.</p>
                %s
                """.formatted(
                BRAND,
                codeBlock(code),
                expiryNote(expiryMinutes),
                expiryMinutes,
                securityNote("You are receiving this email because someone registered a "
                        + BRAND + " account with this address. If that was not you, no action "
                        + "is required: the account stays inactive without this code."));

        return new EmailContent(
                "Your " + BRAND + " verification code",
                layout("Confirm your email address", body));
    }

    public EmailContent passwordResetOtp(String code, long expiryMinutes) {

        String body = """
                <p style="margin:0 0 16px;">We received a request to reset the password for
                your %s account. Use the code below to continue.</p>
                %s
                %s
                <p style="margin:0 0 16px;">The code expires in <strong>%d minutes</strong> and
                can be used once.</p>
                %s
                """.formatted(
                BRAND,
                codeBlock(code),
                expiryNote(expiryMinutes),
                expiryMinutes,
                securityNote("If you did not request a password reset, you can ignore this "
                        + "email. Your current password will keep working."));

        return new EmailContent(
                "Reset your " + BRAND + " password",
                layout("Reset your password", body));
    }

    public EmailContent passwordChanged(OffsetDateTime changedAt) {

        String when = DateTimeFormatter
                .ofPattern("d MMM uuuu, HH:mm 'UTC'", Locale.ENGLISH)
                .withZone(ZoneOffset.UTC)
                .format(changedAt);

        String body = """
                <p style="margin:0 0 16px;">The password for your %s account was changed on
                <strong>%s</strong>.</p>
                <p style="margin:0 0 16px;">For your security, every existing signed-in session
                has been signed out. Sign in again with your new password.</p>
                %s
                """.formatted(
                HtmlUtils.htmlEscape(BRAND),
                HtmlUtils.htmlEscape(when),
                securityNote("If you did not make this change, reset your password immediately "
                        + "and contact your administrator."));

        return new EmailContent(
                "Your " + BRAND + " password was changed",
                layout("Your password was changed", body));
    }

    // ---------------------------------------------------------------- building blocks

    private String codeBlock(String code) {

        return """
                <div style="margin:0 0 20px;padding:18px 24px;background:#f5f7fa;border:1px solid %s;
                            border-radius:10px;text-align:center;">
                  <div style="font-size:12px;letter-spacing:.08em;text-transform:uppercase;
                              color:%s;margin-bottom:8px;">Verification code</div>
                  <div style="font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;
                              font-size:32px;font-weight:700;letter-spacing:.28em;color:%s;">
                    %s
                  </div>
                </div>
                """.formatted(
                BORDER_COLOR,
                MUTED_COLOR,
                TEXT_COLOR,
                HtmlUtils.htmlEscape(code));
    }

    private String expiryNote(long expiryMinutes) {

        return """
                <p style="margin:0 0 16px;font-size:13px;color:%s;">
                  This code is valid for %d minutes.
                </p>
                """.formatted(MUTED_COLOR, expiryMinutes);
    }

    private String securityNote(String message) {

        return """
                <div style="margin:20px 0 0;padding:14px 16px;background:#fff8e6;
                            border-left:3px solid #f0b429;border-radius:4px;
                            font-size:13px;line-height:1.5;color:#5c4813;">
                  %s
                </div>
                """.formatted(HtmlUtils.htmlEscape(message));
    }

    private String layout(String heading, String body) {

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f0f4f8;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                         style="background:#f0f4f8;padding:24px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                               style="max-width:560px;background:#ffffff;border-radius:12px;
                                      overflow:hidden;border:1px solid %s;">
                          <tr>
                            <td style="background:%s;padding:20px 28px;">
                              <span style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',
                                           Roboto,Helvetica,Arial,sans-serif;font-size:18px;
                                           font-weight:700;color:#ffffff;letter-spacing:.01em;">
                                %s
                              </span>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              <h1 style="margin:0 0 16px;font-family:-apple-system,BlinkMacSystemFont,
                                         'Segoe UI',Roboto,Helvetica,Arial,sans-serif;
                                         font-size:20px;line-height:1.3;color:%s;">%s</h1>
                              <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',
                                          Roboto,Helvetica,Arial,sans-serif;font-size:15px;
                                          line-height:1.6;color:%s;">
                                %s
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 28px;border-top:1px solid %s;">
                              <p style="margin:0;font-family:-apple-system,BlinkMacSystemFont,
                                        'Segoe UI',Roboto,Helvetica,Arial,sans-serif;
                                        font-size:12px;line-height:1.5;color:%s;">
                                This is an automated message from %s. Please do not reply.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(heading),
                HtmlUtils.htmlEscape(heading),
                BORDER_COLOR,
                BRAND_COLOR,
                HtmlUtils.htmlEscape(BRAND),
                TEXT_COLOR,
                HtmlUtils.htmlEscape(heading),
                TEXT_COLOR,
                body,
                BORDER_COLOR,
                MUTED_COLOR,
                HtmlUtils.htmlEscape(BRAND));
    }
}
