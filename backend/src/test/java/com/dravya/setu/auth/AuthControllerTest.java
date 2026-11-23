package com.dravya.setu.auth;

import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.common.EmailNotVerifiedException;
import com.dravya.setu.common.GlobalExceptionHandler;
import com.dravya.setu.common.InvalidCredentialsException;
import com.dravya.setu.mail.EmailDeliveryException;
import com.dravya.setu.users.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private HttpServletRequest httpRequest;

    private MockMvc mockMvc;
    private final User principal = new User();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        principal.setEmail("user@example.com");
        principal.setRole("USER");
        principal.setEmailVerified(true);
        org.springframework.test.util.ReflectionTestUtils.setField(principal, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                principal, "createdAt", OffsetDateTime.now());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PrincipalResolver(principal))
                .build();
    }

    @Test
    void registerReturns201() throws Exception {
        doNothing().when(authService).register(any(RegisterRequest.class), any(HttpServletRequest.class));

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"new@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void registerReturns503WhenTheEmailCannotBeSent() throws Exception {
        doThrow(new EmailDeliveryException("smtp down"))
                .when(authService).register(any(RegisterRequest.class), any(HttpServletRequest.class));

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"new@example.com","password":"password123"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DELIVERY_FAILED"));
    }

    @Test
    void verifyEmailReturns204() throws Exception {
        mockMvc.perform(post("/auth/verify-email")
                        .contentType("application/json")
                        .content("""
                                {"email":"new@example.com","otp":"123456"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void verifyEmailMapsAnExpiredCodeTo400() throws Exception {
        doThrow(new ClientErrorException("OTP_EXPIRED", "The code has expired"))
                .when(authService).verifyEmail(any(VerifyEmailRequest.class), any(HttpServletRequest.class));

        mockMvc.perform(post("/auth/verify-email")
                        .contentType("application/json")
                        .content("""
                                {"email":"new@example.com","otp":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OTP_EXPIRED"));
    }

    @Test
    void resendVerificationAlwaysReturns202() throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType("application/json")
                        .content("""
                                {"email":"anyone@example.com"}
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void loginReturns200AndToken() throws Exception {
        when(authService.login(any(LoginRequest.class), any(HttpServletRequest.class)))
                .thenReturn(new AuthResponse("test-jwt-token", "Bearer", 3600));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginReturns401ForBadCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), any(HttpServletRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"test@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginReturns403ForAnUnverifiedAccount() throws Exception {
        when(authService.login(any(LoginRequest.class), any(HttpServletRequest.class)))
                .thenThrow(new EmailNotVerifiedException("Email address is not verified"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void logoutPassesTheBearerTokenToTheService() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isNoContent());

        verify(authService).logout(eq("abc.def.ghi"), any(HttpServletRequest.class));
    }

    @Test
    void meReturnsTheAuthenticatedIdentity() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email_verified").value(true));
    }

    @Test
    void forgotPasswordAlwaysReturns202() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"anyone@example.com"}
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void verifyResetOtpReturnsTheSingleUseTicket() throws Exception {
        when(authService.verifyResetOtp(any(VerifyResetOtpRequest.class), any(HttpServletRequest.class)))
                .thenReturn(new ResetOtpResponse(
                        "ticket-value", OffsetDateTime.now().plusMinutes(10)));

        mockMvc.perform(post("/auth/verify-reset-otp")
                        .contentType("application/json")
                        .content("""
                                {"email":"test@example.com","otp":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset_token").value("ticket-value"))
                .andExpect(jsonPath("$.expires_at").exists());
    }

    @Test
    void resetPasswordAcceptsSnakeCaseBodyAndReturns204() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reset_token":"ticket-value",
                                  "password":"newpassword1",
                                  "confirm_password":"newpassword1"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword(any(ResetPasswordRequest.class), any(HttpServletRequest.class));
    }

    /** Supplies the principal that Spring Security would normally inject. */
    private record PrincipalResolver(User user) implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return User.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return user;
        }
    }
}
