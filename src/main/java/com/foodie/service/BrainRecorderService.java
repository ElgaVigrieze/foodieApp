package com.foodie.service;

import com.foodie.controller.dto.BrainIngestRequest;
import com.foodie.controller.dto.BrainIngestResponse;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.BrainTestResult;
import com.foodie.repository.AppUserRepository;
import com.foodie.repository.BrainTestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Serves the browser recorder (userscript). Every call is authenticated by the
 * per-user recorder token; there is no session user here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrainRecorderService {

    private final BrainTestResultRepository brainTestResultRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public AppUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidBrainTokenException();
        }
        return appUserRepository.findByBrainToken(token)
                .orElseThrow(InvalidBrainTokenException::new);
    }

    /**
     * Called by the userscript when it loads a test page. It lets the Brain page
     * show "recorder detected" even before the first result is submitted, which
     * makes installation/pairing problems obvious.
     */
    @Transactional
    public AppUser touchRecorder(String token) {
        AppUser user = authenticate(token);
        user.setBrainLastSeenAt(Instant.now());
        log.info("Brain recorder heartbeat for user {}", user.getId());
        return appUserRepository.save(user);
    }

    @Transactional
    public BrainIngestResponse ingest(BrainIngestRequest request, String token) {
        AppUser user = authenticate(token);

        if (request == null) {
            throw new IllegalArgumentException("Missing result body");
        }

        // The recorder retries on flaky connections; a client key keeps that idempotent.
        // Scope the lookup to this user so one user's keys can never expose another's result.
        String requestClientKey = BrainResultFactory.blankToNull(request.clientKey());
        if (requestClientKey != null) {
            Optional<BrainTestResult> existing = brainTestResultRepository
                    .findByOwnerIdAndClientKey(user.getId(), requestClientKey);
            if (existing.isPresent()) {
                return BrainIngestResponse.from(existing.get(), true);
            }
        }

        BrainTestResult result = BrainResultFactory.build(
                user,
                request.testType(),
                request.date(),
                request.reactionMs(),
                request.bestReactionMs(),
                request.attempts(),
                request.processingAvgMs(),
                request.processingAccuracy(),
                request.levels(),
                request.roundsPerLevel(),
                requestClientKey,
                request.source()
        );

        BrainTestResult saved = brainTestResultRepository.save(result);

        user.setBrainLastSyncAt(Instant.now());
        appUserRepository.save(user);

        log.info("Brain result saved for user {}: {} {}", user.getId(), saved.getTestType(), saved.getTestDate());
        return BrainIngestResponse.from(saved, false);
    }

    /**
     * Looks up an already-stored result for a retried request. Used when a concurrent
     * duplicate insert loses the race on the unique (owner, clientKey) index.
     */
    @Transactional(readOnly = true)
    public Optional<BrainIngestResponse> findDuplicate(BrainIngestRequest request, String token) {
        AppUser user = authenticate(token);
        if (request == null) {
            return Optional.empty();
        }

        String clientKey = BrainResultFactory.blankToNull(request.clientKey());
        if (clientKey == null) {
            return Optional.empty();
        }

        return brainTestResultRepository.findByOwnerIdAndClientKey(user.getId(), clientKey)
                .map(existing -> BrainIngestResponse.from(existing, true));
    }
}
