package com.dravya.setu.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registrationRejectsBlankMalformedAndWeakCredentials() {
        RegisterRequest blank = new RegisterRequest(); blank.setEmail(" "); blank.setPassword(" ");
        assertFalse(validator.validate(blank).isEmpty());

        RegisterRequest malformed = new RegisterRequest(); malformed.setEmail("not-an-email"); malformed.setPassword("password1");
        assertFalse(validator.validate(malformed).isEmpty());

        RegisterRequest weak = new RegisterRequest(); weak.setEmail("user@example.com"); weak.setPassword("abcdefgh");
        assertFalse(validator.validate(weak).isEmpty());

        RegisterRequest tooLong = new RegisterRequest();
        tooLong.setEmail("user@example.com");
        tooLong.setPassword("a1" + "x".repeat(80));
        assertFalse(validator.validate(tooLong).isEmpty());
    }

    @Test
    void registrationAcceptsValidCredentialsAndLoginRequiresBothFields() {
        RegisterRequest valid = new RegisterRequest(); valid.setEmail("user@example.com"); valid.setPassword("password1");
        assertTrue(validator.validate(valid).isEmpty());

        LoginRequest login = new LoginRequest(); login.setEmail("user@example.com"); login.setPassword(" ");
        assertFalse(validator.validate(login).isEmpty());
    }

    @Test
    void verificationCodeMustBeExactlySixDigits() {

        VerifyEmailRequest missing = new VerifyEmailRequest();
        missing.setEmail("user@example.com");
        assertFalse(validator.validate(missing).isEmpty());

        VerifyEmailRequest shortCode = new VerifyEmailRequest();
        shortCode.setEmail("user@example.com");
        shortCode.setOtp("12345");
        assertFalse(validator.validate(shortCode).isEmpty());

        VerifyEmailRequest letters = new VerifyEmailRequest();
        letters.setEmail("user@example.com");
        letters.setOtp("12a456");
        assertFalse(validator.validate(letters).isEmpty());

        VerifyEmailRequest valid = new VerifyEmailRequest();
        valid.setEmail("user@example.com");
        valid.setOtp("012345");
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void resetOtpRequestValidatesEmailAndCode() {

        VerifyResetOtpRequest invalid = new VerifyResetOtpRequest();
        invalid.setEmail("not-an-email");
        invalid.setOtp("1234567");
        assertFalse(validator.validate(invalid).isEmpty());

        VerifyResetOtpRequest valid = new VerifyResetOtpRequest();
        valid.setEmail("user@example.com");
        valid.setOtp("123456");
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void resetPasswordRequiresATicketAStrongPasswordAndConfirmation() {

        ResetPasswordRequest missingTicket = new ResetPasswordRequest();
        missingTicket.setPassword("newpassword1");
        missingTicket.setConfirmPassword("newpassword1");
        assertFalse(validator.validate(missingTicket).isEmpty());

        ResetPasswordRequest weak = new ResetPasswordRequest();
        weak.setResetToken("ticket");
        weak.setPassword("abcdefgh");
        weak.setConfirmPassword("abcdefgh");
        assertFalse(validator.validate(weak).isEmpty());

        ResetPasswordRequest missingConfirmation = new ResetPasswordRequest();
        missingConfirmation.setResetToken("ticket");
        missingConfirmation.setPassword("newpassword1");
        assertFalse(validator.validate(missingConfirmation).isEmpty());

        ResetPasswordRequest valid = new ResetPasswordRequest();
        valid.setResetToken("ticket");
        valid.setPassword("newpassword1");
        valid.setConfirmPassword("newpassword1");
        assertTrue(validator.validate(valid).isEmpty());
    }
}
