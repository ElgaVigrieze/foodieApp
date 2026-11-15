package com.foodie.controller;

import com.foodie.controller.dto.BrainIngestRequest;
import com.foodie.controller.dto.BrainIngestResponse;
import com.foodie.controller.dto.BrainPingResponse;
import com.foodie.exception.BadRequestException;
import com.foodie.exception.ConflictException;
import com.foodie.exception.InvalidBrainTokenException;
import com.foodie.model.db.AppUser;
import com.foodie.service.BrainRecorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Token-authenticated endpoint used by the browser recorder.
 * CSRF is disabled for this path in {@link com.foodie.config.SecurityConfig};
 * the per-user token is the credential.
 *
 * <p>Errors are thrown, not returned: {@link ApiExceptionHandler} turns them into
 * HTTP responses (invalid token: 401, invalid result: 400, unsaveable result: 409).
 */
@Slf4j
@RestController
@RequestMapping("/api/brain")
@RequiredArgsConstructor
public class BrainApiController {

    private final BrainRecorderService brainRecorderService;

    /**
     * Records a brain-test result sent by the browser recorder.
     *
     * <p>If the same result was already stored (a retry that raced the original request and hit the
     * unique-key constraint), the existing result is returned instead, so retries are idempotent.
     *
     * @throws InvalidBrainTokenException if the token is missing or invalid
     * @throws BadRequestException                           if the result fails validation
     * @throws ConflictException                             if the result could not be saved
     */
    @PostMapping("/ingest")
    public BrainIngestResponse ingest(@RequestHeader(value = "X-Brain-Token", required = false) String token,
                                      @RequestBody(required = false) BrainIngestRequest request) {
        try {
            return brainRecorderService.ingest(request, token);
        } catch (DataIntegrityViolationException e) {
            return brainRecorderService.findDuplicate(request, token)
                    .orElseThrow(() -> {
                        log.warn("Brain result could not be saved and is not a duplicate: {}", e.getMessage(), e);
                        return new ConflictException("Result could not be saved");
                    });
        }
    }

    /**
     * Verifies that a recorder token is valid. Used by the userscript to check pairing.
     *
     * @throws InvalidBrainTokenException if the token is missing or invalid
     */
    @GetMapping("/ping")
    public BrainPingResponse ping(@RequestHeader(value = "X-Brain-Token", required = false) String token) {
        AppUser user = brainRecorderService.authenticate(token);
        return new BrainPingResponse("ok", displayName(user), Instant.now());
    }

    /**
     * The userscript calls this when a test page loads. It is not a result; it
     * just proves the recorder is installed, paired, and able to reach Foodie.
     *
     * @throws InvalidBrainTokenException if the token is missing or invalid
     */
    @PostMapping("/heartbeat")
    public BrainPingResponse heartbeat(@RequestHeader(value = "X-Brain-Token", required = false) String token) {
        AppUser user = brainRecorderService.touchRecorder(token);
        return new BrainPingResponse("ok", displayName(user), Instant.now());
    }

    /**
     * Picks a name to show for the user: their name, else their email, else a default.
     */
    private static String displayName(AppUser user) {
        return user.getName() != null && !user.getName().isBlank()
                ? user.getName()
                : (user.getEmail() != null ? user.getEmail() : "Foodie user");
    }
}
