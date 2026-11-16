package com.foodie.controller.dto;

import jakarta.validation.constraints.Size;

/** Request body for parsing a spoken food log sentence. Blank text is reported by the service. */
public record VoiceParseRequest(@Size(max = 1000) String text) {
    public VoiceParseRequest {
        if (text == null) text = "";
    }
}
