package com.foodie.controller;

import com.foodie.controller.dto.BrainIngestRequest;
import com.foodie.controller.dto.BrainIngestResponse;
import com.foodie.controller.dto.BrainPingResponse;
import com.foodie.model.db.AppUser;
import com.foodie.service.BrainRecorderService;
import com.foodie.service.InvalidBrainTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
@Slf4j
public class BrainApiController {

    private final BrainRecorderService brainRecorderService;

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestHeader(value = "X-Brain-Token", required = false) String token,
                                    @RequestBody(required = false) BrainIngestRequest request) {
        try {
            BrainIngestResponse response = brainRecorderService.ingest(request, token);
            return ResponseEntity.ok(response);
        } catch (InvalidBrainTokenException e) {
            log.warn("Brain ingest rejected: missing or invalid recorder token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage() == null ? "Invalid result" : e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent retry with the same clientKey won the race on the unique index.
            return brainRecorderService.findDuplicate(request, token)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Result could not be saved")));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadableBody() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Malformed request body (check testType and date format)"));
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping(@RequestHeader(value = "X-Brain-Token", required = false) String token) {
        try {
            AppUser user = brainRecorderService.authenticate(token);
            return ResponseEntity.ok(new BrainPingResponse("ok", displayName(user), Instant.now()));
        } catch (InvalidBrainTokenException e) {
            log.warn("Brain ping rejected: missing or invalid recorder token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * The userscript calls this when a test page loads. It is not a result; it
     * just proves the recorder is installed, paired, and able to reach Foodie.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@RequestHeader(value = "X-Brain-Token", required = false) String token,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                       @RequestParam(required = false) String page) {
        try {
            AppUser user = brainRecorderService.touchRecorder(token, page, userAgent);
            return ResponseEntity.ok(new BrainPingResponse("ok", displayName(user), Instant.now()));
        } catch (InvalidBrainTokenException e) {
            log.warn("Brain heartbeat rejected: missing or invalid recorder token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private static String displayName(AppUser user) {
        return user.getName() != null && !user.getName().isBlank()
                ? user.getName()
                : (user.getEmail() != null ? user.getEmail() : "Foodie user");
    }
}
