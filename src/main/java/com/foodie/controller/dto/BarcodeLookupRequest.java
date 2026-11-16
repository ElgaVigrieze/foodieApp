package com.foodie.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for a barcode lookup. */
public record BarcodeLookupRequest(@NotBlank @Size(max = 64) String barcode) {
    public BarcodeLookupRequest {
        if (barcode != null) barcode = barcode.trim();
    }
}
