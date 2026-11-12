package com.foodie.controller;

import com.foodie.controller.dto.BrainIngestRequest;
import com.foodie.controller.dto.BrainIngestResponse;
import com.foodie.controller.dto.BrainPingResponse;
import com.foodie.model.db.AppUser;
import com.foodie.service.BrainService;
import com.foodie.service.InvalidBrainTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Token-authenticated endpoint used by the browser recorder.
 * CSRF is disabled for this path in {@link com.foodie.config.SecurityConfig};
 * the per-user token is the credential.
 */
@RestController
@RequestMapping("/api/brain")
@RequiredArgsConstructor
public class BrainApiController {

    private final BrainService brainService;

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestHeader(value = "X-Brain-Token", required = false) String token,
                                    @RequestBody(required = false) BrainIngestRequest request) {
        try {
            BrainIngestResponse response = brainService.ingest(request, token);
            return ResponseEntity.ok(response);
        } catch (InvalidBrainTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage() == null ? "Invalid result" : e.getMessage()));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping(@RequestHeader(value = "X-Brain-Token", required = false) String token) {
        try {
            AppUser user = brainService.authenticate(token);
            String name = user.getName() != null && !user.getName().isBlank()
                    ? user.getName()
                    : (user.getEmail() != null ? user.getEmail() : "Foodie user");
            return ResponseEntity.ok(new BrainPingResponse("ok", name, Instant.now()));
        } catch (InvalidBrainTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
