package com.foodie.model;

import jakarta.persistence.*;
import lombok.*;

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
}
