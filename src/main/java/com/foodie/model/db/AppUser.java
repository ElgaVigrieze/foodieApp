package com.foodie.model.db;

import com.foodie.model.HouseholdRole;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "app_users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String googleId;

    private String email;
    private String name;
    private String pictureUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "household_id")
    private Household household;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    @Builder.Default
    private HouseholdRole householdRole = HouseholdRole.MEMBER;

    public boolean isOwner() {
        return HouseholdRole.OWNER.equals(householdRole);
    }

    private BigDecimal targetCalories;
    private BigDecimal targetFiber;
    private BigDecimal targetCarbs;
    private BigDecimal targetFat;
    private BigDecimal targetProtein;
    private BigDecimal targetCost;

    // ── Brain test recorder ──────────────────────────────────────────────────

    /** Secret token used by the browser recorder to post results without a session. */
    @Column(name = "brain_token", unique = true, length = 64)
    private String brainToken;

    @Column(name = "brain_token_created_at")
    private Instant brainTokenCreatedAt;

    /** Last time a result was received from the browser recorder. */
    @Column(name = "brain_last_sync_at")
    private Instant brainLastSyncAt;
}