package com.foodie.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for calendar-related operations.
 */
public final class CalendarUtils {

    private CalendarUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Returns the Monday of the week containing the given date.
     *
     * @param date any date
     * @return the Monday of that week
     */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Builds a calendar grid for the given month.
     * Returns a list of weeks, where each week is a list of 7 day-numbers.
     * Empty cells (padding) are represented as 0.
     *
     * @param yearMonth the year and month to build the calendar for
     * @return list of weeks, each containing 7 integers (0 = empty padding, 1-31 = day number)
     */
    public static List<List<Integer>> buildWeeks(YearMonth yearMonth) {
        int firstDow = yearMonth.atDay(1).getDayOfWeek().getValue(); // 1=Mon..7=Sun
        int daysInMonth = yearMonth.lengthOfMonth();

        List<List<Integer>> weeks = new ArrayList<>();
        List<Integer> currentWeek = new ArrayList<>();

        // Pad start (Mon=1, so pad (firstDow-1) blanks)
        for (int i = 1; i < firstDow; i++) {
            currentWeek.add(0);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            currentWeek.add(day);
            if (currentWeek.size() == 7) {
                weeks.add(currentWeek);
                currentWeek = new ArrayList<>();
            }
        }

        // Pad end of last week if needed
        if (!currentWeek.isEmpty()) {
            while (currentWeek.size() < 7) {
                currentWeek.add(0);
            }
            weeks.add(currentWeek);
        }

        return weeks;
    }
}
