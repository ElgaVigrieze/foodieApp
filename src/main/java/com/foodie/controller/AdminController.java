package com.foodie.controller;

import com.foodie.service.CurrentUserService;
import com.foodie.service.DatabaseMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for monitoring and management.
 * Restricted to admin user(s) only.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DatabaseMonitoringService monitoringService;
    private final CurrentUserService currentUserService;

    @Value("${app.monitoring.admin-email:}")
    private String adminEmail;

    /**
     * Get database statistics.
     * Only accessible to admin users.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(monitoringService.getStats());
    }

    /**
     * Get top users by data volume.
     * Only accessible to admin users.
     */
    @GetMapping("/stats/top-users")
    public ResponseEntity<Map<String, Object>> getTopUsers() {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(monitoringService.getTopUsers());
    }

    private boolean isAdmin() {
        var user = currentUserService.getCurrentUser();
        if (user == null) return false;

        // Check if user's email matches admin email
        if (adminEmail != null && !adminEmail.isBlank()) {
            return adminEmail.equalsIgnoreCase(user.getEmail());
        }

        // Fallback: first user (owner) is admin
        return user.getId() == 1L;
    }
}
