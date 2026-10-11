package com.foodie.controller.dto;

import java.math.BigDecimal;

/**
 * Status of a single day for calendar display.
 */
public record DayStatus(
        boolean hasEntries,
        Boolean allTargetsMet,
        BigDecimal calories,
        BigDecimal netCalories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal cost
) {
    /**
     * Creates a DayStatus for days with no entries.
     */
    public static DayStatus noEntries() {
        return new DayStatus(false, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
