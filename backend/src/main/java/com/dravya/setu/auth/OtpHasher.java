package com.dravya.setu.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Hashes OTPs with HMAC-SHA-256 using a server-side pepper.
 *
 * <p>A bare SHA-256 digest of a six-digit code is reversible by brute force if the database
 * leaks: there are only a million candidates. Keying the digest with a secret that never
 * reaches the database removes that shortcut. The digest is also bound to the user and the
 * purpose, so a code cannot be replayed against another account or flow.</p>
 */
@Component
public class OtpHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int DIGEST_BYTES = 32;

    private final byte[] key;

    public OtpHasher(@Value("${app.otp.hash-secret}") String secret) {

        if (secret == null || secret.isBlank() || secret.length() < 16) {
            throw new IllegalStateException(
                    "app.otp.hash-secret must be at least 16 characters; "
                            + "set OTP_HASH_SECRET or JWT_SECRET");
        }

        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(Long userId, OtpPurpose purpose, String otp) {

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));

            String material = userId + ":" + purpose.name() + ":" + otp;

            byte[] digest = mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);

        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not hash OTP", exception);
        }
    }

    /** Constant-time comparison, so a wrong code cannot be discovered byte by byte. */
    public boolean matches(Long userId, OtpPurpose purpose, String otp, String expectedHash) {

        if (otp == null || expectedHash == null) {
            return false;
        }

        byte[] actual = hash(userId, purpose, otp).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * Performs the same work as a real check without touching the database. Callers use it
     * when the account does not exist so that response timing does not reveal that fact.
     */
    public void simulateCheck(String otp) {
        hash(-1L, OtpPurpose.EMAIL_VERIFICATION, otp == null ? "" : otp);
    }

    public static int digestLength() {
        return DIGEST_BYTES * 2;
    }

    /**
     * Returns the secret key bytes for external use (e.g., pending registration).
     */
    public byte[] getKey() {
        return key;
    }

    /**
     * Hashes an OTP for pending registration using the provided secret key.
     * This is a stateless utility for when no userId exists yet.
     */
    public static String hashForPendingRegistration(String otp, byte[] secretKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, ALGORITHM));

            byte[] digest = mac.doFinal(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);

        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not hash OTP", exception);
        }
    }

    /**
     * Constant-time comparison for pending registration OTP.
     */
    public static boolean matchesForPendingRegistration(String otp, String expectedHash, byte[] secretKey) {
        if (otp == null || expectedHash == null) {
            return false;
        }
        String actual = hashForPendingRegistration(otp, secretKey);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
