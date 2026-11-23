package com.dravya.setu.auth;

/**
 * Why an OTP exists. A code issued for one purpose can never be used for the other.
 */
public enum OtpPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    PENDING_REGISTRATION
}
