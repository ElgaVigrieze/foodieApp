package com.foodie.controller;

import com.foodie.service.CurrentUserService;
import com.foodie.service.DatabaseMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for monitoring and management.
 * Restricted to admin user(s) only; other users get a 403 from {@link ApiExceptionHandler}.
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
     *
     * @throws SecurityException if the current user is not an admin
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        requireAdmin();
        return monitoringService.getStats();
    }

    /**
     * Get top users by data volume.
     *
     * @throws SecurityException if the current user is not an admin
     */
    @GetMapping("/stats/top-users")
    public Map<String, Object> getTopUsers() {
        requireAdmin();
        return monitoringService.getTopUsers();
    }

    private void requireAdmin() {
        if (!isAdmin()) {
            throw new SecurityException("Admin access required");
        }
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
