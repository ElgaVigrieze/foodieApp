package com.foodie.model;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "workouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private WorkoutType type;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String youtubeUrl;

    private Integer totalMinutes;

    /** Estimated calories burned for this workout. */
    private Integer estimatedKcal;

    /** Actual calories burned (filled in after completion). */
    private Integer actualKcal;

    @Column(columnDefinition = "TEXT")
    private String setsJson;
}
