package com.dravya.setu.knowledge;

/**
 * Raised when a caller asks for speech-to-text, which the deployed knowledge service does not
 * expose. Mapped to {@code 501 VOICE_INPUT_UNSUPPORTED} by the global exception handler.
 */
public class VoiceNotSupportedException extends RuntimeException {

    public VoiceNotSupportedException(String message) {
        super(message);
    }
}
