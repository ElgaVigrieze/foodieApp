package com.foodie.controller;

import com.foodie.service.MealPlanService;
import com.foodie.service.MealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests for {@code POST /meal-plan/copy-week} in {@link MealPlanController}. */
@ExtendWith(MockitoExtension.class)
class MealPlanControllerTest {

    @Mock MealPlanService mealPlanService;
    @Mock MealService mealService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MealPlanController(mealPlanService, mealService)).build();
    }

    @Test
    void copyWeekCallsServiceSetsSuccessFlashAndRedirectsToViewedWeek() throws Exception {
        when(mealPlanService.copyWeek(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9)))
                .thenReturn(new MealPlanService.CopyResult(12, 0, 2));

        mockMvc.perform(post("/meal-plan/copy-week")
                        .param("sourceWeek", "2026-11-02").param("weekStart", "2026-11-09"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meal-plan?week=2026-11-09"))
                .andExpect(flash().attribute("success", "Copied 12 entries (2 archived meals not copied)."));
    }

    @Test
    void copyWeekPropagatesServiceRefusalsToTheExceptionHandler() {
        when(mealPlanService.copyWeek(any(), any()))
                .thenThrow(new com.foodie.exception.BadRequestException("Nothing to copy from that week."));

        assertThatThrownBy(() -> mockMvc.perform(post("/meal-plan/copy-week")
                .param("sourceWeek", "2026-11-02").param("weekStart", "2026-11-09")))
                .hasRootCauseInstanceOf(com.foodie.exception.BadRequestException.class);
    }

    @Test
    void copyWeekRejectsMissingOrMalformedParametersWithoutCallingService() throws Exception {
        String[][] bad = {
                {"weekStart", "2026-11-09"},
                {"sourceWeek", "2026-11-02"},
                {"sourceWeek", "not-a-date", "weekStart", "2026-11-09"},
                {"sourceWeek", "2026-11-02", "weekStart", "31/12/2026"}};
        for (String[] params : bad) {
            var req = post("/meal-plan/copy-week");
            for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
            mockMvc.perform(req).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(mealPlanService);
    }
}
