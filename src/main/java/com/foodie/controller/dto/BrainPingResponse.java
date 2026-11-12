package com.foodie.controller.dto;

import java.time.Instant;

public record BrainPingResponse(String status, String user, Instant serverTime) {
}
