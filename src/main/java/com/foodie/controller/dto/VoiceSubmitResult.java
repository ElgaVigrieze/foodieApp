package com.foodie.controller.dto;

/**
 * Result of submitting a voice-parsed food log entry.
 */
public record VoiceSubmitResult(
        boolean success,
        String error
) {
    public static VoiceSubmitResult ok() {
        return new VoiceSubmitResult(true, null);
    }

    public static VoiceSubmitResult fail(String error) {
        return new VoiceSubmitResult(false, error);
    }
}
