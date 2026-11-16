package com.foodie.config;

import com.foodie.model.db.AppUser;
import com.foodie.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Every user must be in a household: all products, meals, plans and shopping lists belong to one.
 * A logged-in user who has not created or joined a household yet is sent to the setup page
 * (or, for API calls, gets a 403) until they do.
 *
 * <p>Requests without a logged-in user pass through untouched: Spring Security has already
 * decided whether they are allowed (e.g. the token-authenticated brain endpoints).
 */
@Component
@RequiredArgsConstructor
public class HouseholdRequiredInterceptor implements HandlerInterceptor {

    static final String SETUP_PAGE = "/household";

    private final CurrentUserService currentUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        AppUser user = currentUserService.getCurrentUser();
        if (user == null || user.getHousehold() != null) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Create or join a household first\"}");
        } else {
            response.sendRedirect(request.getContextPath() + SETUP_PAGE);
        }
        return false;
    }
}
