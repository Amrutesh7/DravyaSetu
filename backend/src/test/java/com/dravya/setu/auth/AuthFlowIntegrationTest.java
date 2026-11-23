package com.dravya.setu.auth;

import com.dravya.setu.TestFlywayConfig;
import com.dravya.setu.mail.EmailSender;
import com.dravya.setu.users.User;
import com.dravya.setu.users.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authentication flow against a real PostgreSQL schema (Flyway V1–V22) and the real
 * Spring Security filter chain. SMTP is replaced by a capturing sender, so the code that reaches
 * the user's inbox is what the test submits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestFlywayConfig.class)
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String NEW_PASSWORD = "newpassword456";
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PendingRegistrationRepository pendingRegistrationRepository;

    @MockitoBean private EmailSender emailSender;

    @Test
    void registrationVerificationLoginAndPasswordResetWorkEndToEnd() throws Exception {

        String email = "flow-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        // ---------------------------------------------------------------- registration
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(registerBody(email, PASSWORD)))
                .andExpect(status().isCreated());

        String verificationCode = latestEmailCode();
        assertNotNull(verificationCode, "the verification email should contain a code");

        // Account must NOT exist yet - pending registration holds the data until OTP verification.
        assertTrue(userRepository.findByEmail(email).isEmpty(),
                "registration must not create a user before OTP verification");
        assertTrue(pendingRegistrationRepository.findByEmail(email).isPresent(),
                "a pending registration should hold the email/password until verification");

        // ---------------------------------------------------------------- unverified login
        // No account exists yet, so login is rejected like any unknown address.
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // ---------------------------------------------------------------- verification
        String wrongCode = verificationCode.equals("000000") ? "111111" : "000000";

        mockMvc.perform(post("/auth/verify-email")
                        .contentType("application/json")
                        .content(verifyBody(email, wrongCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));

        mockMvc.perform(post("/auth/verify-email")
                        .contentType("application/json")
                        .content(verifyBody(email, verificationCode)))
                .andExpect(status().isNoContent());

        assertTrue(userRepository.findByEmail(email).orElseThrow().isEmailVerified());
        assertNotNull(userRepository.findByEmail(email).orElseThrow().getEmailVerifiedAt());

        /*
         * Verification is idempotent: the account is already verified, so a repeated
         * submission is accepted without re-checking the code. Single-use enforcement for the
         * code itself is asserted against the reset flow below, where a code still has power.
         */
        mockMvc.perform(post("/auth/verify-email")
                        .contentType("application/json")
                        .content(verifyBody(email, verificationCode)))
                .andExpect(status().isNoContent());

        // ---------------------------------------------------------------- login
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginBody(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        String firstToken = loginToken(email, PASSWORD);

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email_verified").value(true));

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/identifications"))
                .andExpect(status().isUnauthorized());

        // ---------------------------------------------------------------- password reset
        clearInvocations(emailSender);

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        String resetCode = latestEmailCode();
        assertNotNull(resetCode, "the reset email should contain a code");

        // Unknown addresses get exactly the same response.
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/auth/verify-reset-otp")
                        .contentType("application/json")
                        .content(verifyBody(email, wrongCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));

        MvcResult verified = mockMvc.perform(post("/auth/verify-reset-otp")
                        .contentType("application/json")
                        .content(verifyBody(email, resetCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset_token").isNotEmpty())
                .andReturn();

        String resetTicket = JsonPath.read(
                verified.getResponse().getContentAsString(), "$.reset_token");

        // The reset code is consumed by step 2 and cannot be used to obtain a second ticket.
        mockMvc.perform(post("/auth/verify-reset-otp")
                        .contentType("application/json")
                        .content(verifyBody(email, resetCode)))
                .andExpect(status().isBadRequest());

        // ---------------------------------------------------------------- new password
        mockMvc.perform(post("/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"reset_token":"%s","password":"%s","confirm_password":"somethingelse1"}
                                """.formatted(resetTicket, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"reset_token":"%s","password":"%s","confirm_password":"%s"}
                                """.formatted(resetTicket, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // Still one account, with a replaced hash: no duplicate user was created.
        List<User> accounts = userRepository.findAll().stream()
                .filter(user -> user.getEmail().equals(email))
                .toList();
        assertEquals(1, accounts.size());
        assertNotEquals(PASSWORD, accounts.get(0).getPasswordHash());
        assertEquals(1, accounts.get(0).getTokenVersion());

        // The ticket is single use.
        mockMvc.perform(post("/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"reset_token":"%s","password":"%s","confirm_password":"%s"}
                                """.formatted(resetTicket, "anotherpass1", "anotherpass1")))
                .andExpect(status().isBadRequest());

        // A token minted before the reset is dead.
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());

        // ---------------------------------------------------------------- credentials changed
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isUnauthorized());

        String secondToken = loginToken(email, NEW_PASSWORD);

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());

        // ---------------------------------------------------------------- logout
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private String latestEmailCode() {

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce())
                .send(anyString(), anyString(), html.capture());

        List<String> bodies = html.getAllValues();

        for (int index = bodies.size() - 1; index >= 0; index--) {
            Matcher matcher = SIX_DIGITS.matcher(bodies.get(index));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private String loginToken(String email, String password) throws Exception {

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private static String registerBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String loginBody(String email, String password) {
        return registerBody(email, password);
    }

    private static String verifyBody(String email, String code) {
        return """
                {"email":"%s","otp":"%s"}
                """.formatted(email, code);
    }
}
