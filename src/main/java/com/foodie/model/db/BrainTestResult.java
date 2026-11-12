package com.foodie.model.db;

import com.foodie.model.BrainTestType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "brain_test_results", indexes = {
        @Index(name = "idx_brain_owner_date", columnList = "owner_id, test_date"),
        @Index(name = "idx_brain_owner_client_key", columnList = "owner_id, client_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrainTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private BrainTestType testType;

    /** The user's local date for the test (not the server's date). */
    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Official reaction-time score displayed after the 5 rounds (average, ms). */
    private Integer reactionMs;

    /** Fastest individual round the recorder saw (ms), if available. */
    private Integer bestReactionMs;

    /** Comma-separated individual round times captured from the page. */
    @Column(columnDefinition = "TEXT")
    private String attemptsCsv;

    private Integer attemptsCount;

    /** Processing-speed average response time (ms). */
    private Integer processingAvgMs;

    /** Processing-speed accuracy (0-100). */
    private Integer processingAccuracy;

    private Integer levels;

    private Integer roundsPerLevel;

    /** Random key supplied by the recorder so retries cannot create duplicates. */
    @Column(name = "client_key", length = 64)
    private String clientKey;

    /** userscript or manual */
    private String source;

    public boolean isReactionTime() {
        return testType == BrainTestType.REACTION_TIME;
    }

    public boolean isProcessingSpeed() {
        return testType == BrainTestType.PROCESSING_SPEED;
    }
}
