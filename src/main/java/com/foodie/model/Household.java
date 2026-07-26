package com.foodie.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "households")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Household {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String inviteCode;

    @OneToMany(mappedBy = "household", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AppUser> members = new ArrayList<>();

    @PrePersist
    private void generateInviteCode() {
        if (inviteCode == null) {
            inviteCode = "FOOD-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        }
    }
}
