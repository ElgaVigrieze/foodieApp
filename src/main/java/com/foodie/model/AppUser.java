package com.foodie.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Application user, linked to Google OAuth2.
 */
@Entity
@Table(name = "app_users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Google's subject identifier (unique per user). */
    @Column(unique = true, nullable = false)
    private String googleId;

    private String email;

    private String name;

    private String pictureUrl;
}
