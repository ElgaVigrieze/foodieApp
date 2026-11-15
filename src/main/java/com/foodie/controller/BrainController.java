package com.foodie.controller;

import com.foodie.controller.dto.BrainMonthSummary;
import com.foodie.model.BrainTestType;
import com.foodie.service.BrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/brain")
@RequiredArgsConstructor
public class BrainController {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SYNC_LABEL =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    private final BrainService brainService;

    /** Cookie set by the Brain page so the server uses the user's timezone, not its own. */
    static final String TZ_COOKIE = "brainTz";

    @GetMapping
    public String index(@RequestParam(required = false) String month,
                        @CookieValue(value = TZ_COOKIE, required = false) String tzCookie,
                        Model model) {
        ZoneId zone = parseZone(tzCookie);
        YearMonth selected = parseMonth(month, zone);

        BrainMonthSummary summary = brainService.getMonth(selected);
        BrainService.RecorderStatus status = brainService.getRecorderStatus();
        Instant lastSync = status.lastSyncAt();

        model.addAttribute("summary", summary);
        model.addAttribute("token", status.token());
        model.addAttribute("monthLabel", selected.format(MONTH_LABEL));
        model.addAttribute("monthParam", selected.toString());
        model.addAttribute("prevMonthParam", selected.minusMonths(1).toString());
        model.addAttribute("nextMonthParam", selected.plusMonths(1).toString());
        model.addAttribute("currentMonthParam", YearMonth.now(zone).toString());
        model.addAttribute("daysInMonth", selected.lengthOfMonth());
        model.addAttribute("today", LocalDate.now(zone));
        model.addAttribute("testTypes", BrainTestType.values());
        model.addAttribute("lastSyncInstant", lastSync);
        model.addAttribute("lastSyncLabel", lastSync == null ? null
                : SYNC_LABEL.format(lastSync.atZone(zone)));
        model.addAttribute("recorderDetected", status.lastSeenAt() != null);
        return "brain/index";
    }

    @PostMapping("/manual")
    public String addManual(@RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam(required = false) String testType,
                            @RequestParam(required = false) Integer reactionMs,
                            @RequestParam(required = false) Integer processingAvgMs,
                            @RequestParam(required = false) Integer processingAccuracy,
                            @CookieValue(value = TZ_COOKIE, required = false) String tzCookie,
                            RedirectAttributes ra) {
        LocalDate resultDate = date != null ? date : LocalDate.now(parseZone(tzCookie));
        try {
            brainService.addManual(resultDate, parseTestType(testType),
                    reactionMs, processingAvgMs, processingAccuracy);
            ra.addFlashAttribute("success", "Result saved.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/brain?month=" + YearMonth.from(resultDate);
    }

    @PostMapping("/results/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String month,
                         RedirectAttributes ra) {
        try {
            brainService.delete(id);
            ra.addFlashAttribute("success", "Result deleted.");
        } catch (NoSuchElementException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/brain" + (month == null || month.isBlank() ? "" : "?month=" + month);
    }

    @PostMapping("/token/regenerate")
    public String regenerateToken(RedirectAttributes ra) {
        brainService.regenerateToken();
        ra.addFlashAttribute("success",
                "New recorder token created. Open a test from this page again to reconnect your browser.");
        return "redirect:/brain";
    }

    private static YearMonth parseMonth(String month, ZoneId zone) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(zone);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            return YearMonth.now(zone);
        }
    }

    private static ZoneId parseZone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private static BrainTestType parseTestType(String value) {
        try {
            return value == null ? null : BrainTestType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null; // the service reports "Please choose a test type"
        }
    }
}
