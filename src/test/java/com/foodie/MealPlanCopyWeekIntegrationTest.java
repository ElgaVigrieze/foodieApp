package com.foodie;

import com.foodie.model.MealCategory;
import com.foodie.model.MealSlot;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.Household;
import com.foodie.model.db.Meal;
import com.foodie.model.db.MealPlan;
import com.foodie.model.db.MealPlanEntry;
import com.foodie.repository.AppUserRepository;
import com.foodie.repository.HouseholdRepository;
import com.foodie.repository.MealPlanRepository;
import com.foodie.repository.MealRepository;
import com.foodie.support.DatabaseCleaner;
import com.foodie.support.PostgresTestConfig;
import com.foodie.util.CalendarUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.FlashMap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static com.foodie.support.TestSessions.maskedCsrf;
import static com.foodie.support.TestSessions.sessionFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Copy-week end to end ({@code POST /meal-plan/copy-week}): the real exception handling, template and
 * database. The copy rules themselves are unit-tested in {@code MealPlanServiceTest}; rule and case
 * numbers refer to {@code docs/specs/copy-week-plan.md}. Household isolation is in
 * {@link HouseholdIsolationTest}.
 */
@SpringBootTest
@Import(PostgresTestConfig.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MealPlanCopyWeekIntegrationTest {

    /** The copy form on the weekly page. */
    private static final String COPY_FORM = "action=\"/meal-plan/copy-week\"";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired TransactionTemplate tx;
    @Autowired AppUserRepository users;
    @Autowired HouseholdRepository households;
    @Autowired MealRepository meals;
    @Autowired MealPlanRepository mealPlans;

    /** The current week (W in the spec). */
    LocalDate w;
    Household household;
    AppUser alice;
    Meal chili, pasta, oldStew;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbc);
        w = CalendarUtils.weekStart(LocalDate.now());
        household = households.save(Household.builder().name("A").build());
        alice = users.save(AppUser.builder().googleId("g-alice").email("alice@example.com")
                .name("Alice").household(household).build());
        chili = meal("Chili", false);
        pasta = meal("Pasta", false);
        oldStew = meal("Old stew", true);

        // Source week: a prepared chili, a pasta and an archived stew.
        plan(w, false,
                entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 4, true),
                entry(DayOfWeek.TUESDAY, MealSlot.DINNER, pasta, 2, false),
                entry(DayOfWeek.WEDNESDAY, MealSlot.DINNER, oldStew, 2, false));
    }

    // ── Success (AC1, R7, R13) ───────────────────────────────────────────────

    @Test
    void copyAddsTheEntriesAndThePageShowsTheResult() throws Exception {
        plan(w.plusWeeks(1), false); // viewing a week creates its plan

        FlashMap flashMap = postCopy(w, w.plusWeeks(1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meal-plan?week=" + w.plusWeeks(1)))
                .andExpect(flash().attribute("success", "Copied 2 entries (1 archived meal not copied)."))
                .andReturn().getFlashMap();

        assertThat(entries(w.plusWeeks(1)))
                .extracting(e -> e.getMeal().getName(), MealPlanEntry::getServings, MealPlanEntry::isPrepared)
                .containsExactlyInAnyOrder(tuple("Chili", 4, false), tuple("Pasta", 2, false));
        assertThat(entries(w))
                .extracting(e -> e.getMeal().getName(), MealPlanEntry::isPrepared)
                .containsExactlyInAnyOrder(tuple("Chili", true), tuple("Pasta", false), tuple("Old stew", false));

        showWeek(w.plusWeeks(1), flashMap)
                .andExpect(content().string(containsString("Copied 2 entries (1 archived meal not copied).")));
    }

    // ── Refusals go back to the viewed week with the message (D3, AC3-AC5) ──

    @Test
    void frozenTargetIsRefusedAndThePageShowsTheError() throws Exception {
        plan(w.plusWeeks(3), true, entry(DayOfWeek.FRIDAY, MealSlot.LUNCH, chili, 1, false));

        FlashMap flashMap = postCopy(w, w.plusWeeks(3))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meal-plan?week=" + w.plusWeeks(3)))
                .andExpect(flash().attribute("error", "The target week is frozen. Unfreeze it first."))
                .andReturn().getFlashMap();

        assertThat(entries(w.plusWeeks(3))).hasSize(1);
        showWeek(w.plusWeeks(3), flashMap)
                .andExpect(content().string(containsString("The target week is frozen. Unfreeze it first.")));
    }

    @Test
    void weeksOutsideTheAllowedRangeAreRefused() throws Exception {
        postCopy(w.plusWeeks(1), w.plusWeeks(2))
                .andExpect(redirectedUrl("/meal-plan?week=" + w.plusWeeks(2)))
                .andExpect(flash().attribute("error", "You can only copy from the current week or an earlier week."));
        postCopy(w.minusWeeks(1), w.minusWeeks(2))
                .andExpect(redirectedUrl("/meal-plan?week=" + w.minusWeeks(2)))
                .andExpect(flash().attribute("error", "You can only copy into the current week or a later week."));

        assertThat(planCount()).isEqualTo(1);
    }

    @Test
    void nothingToCopyCreatesNoPlanForEitherWeek() throws Exception {
        postCopy(w.minusWeeks(7), w.plusWeeks(4))
                .andExpect(redirectedUrl("/meal-plan?week=" + w.plusWeeks(4)))
                .andExpect(flash().attribute("error", "Nothing to copy from that week."));

        assertThat(mealPlans.findByWeekStartAndHouseholdId(w.minusWeeks(7), household.getId())).isEmpty();
        assertThat(mealPlans.findByWeekStartAndHouseholdId(w.plusWeeks(4), household.getId())).isEmpty();
    }

    @Test
    void invalidFormInputIsReportedAndWritesNothing() throws Exception {
        plan(w.plusWeeks(1), false);
        String target = w.plusWeeks(1).toString();

        submit("/meal-plan/copy-week", referer(w.plusWeeks(1)), "sourceWeek", "not-a-date", "weekStart", target)
                .andExpect(redirectedUrl("/meal-plan?week=" + target))
                .andExpect(flash().attribute("error", containsString("sourceWeek")));
        submit("/meal-plan/copy-week", referer(w.plusWeeks(1)), "weekStart", target)
                .andExpect(redirectedUrl("/meal-plan?week=" + target))
                .andExpect(flash().attribute("error", containsString("sourceWeek")));

        assertThat(entries(w.plusWeeks(1))).isEmpty();
    }

    // ── The copy control (AC8) ───────────────────────────────────────────────

    @Test
    void copyControlIsShownOnlyOnCurrentOrLaterWeeks() throws Exception {
        showWeek(w).andExpect(content().string(containsString(COPY_FORM)));
        showWeek(w.plusWeeks(2)).andExpect(content().string(containsString(COPY_FORM)));
        showWeek(w.minusWeeks(1)).andExpect(content().string(not(containsString(COPY_FORM))));
    }

    // ── Single-entry copy is unchanged (case E) ──────────────────────────────

    @Test
    void singleEntryCopyStillCopiesWithinThePlan() throws Exception {
        MealPlan plan = mealPlans.findByWeekStartAndHouseholdId(w, household.getId()).orElseThrow();
        Long pastaEntryId = entries(w).stream().filter(e -> e.getMeal().getName().equals("Pasta"))
                .findFirst().orElseThrow().getId();

        submit("/meal-plan/copy", referer(w), "planId", plan.getId().toString(), "entryId", pastaEntryId.toString(),
                "newDay", "FRIDAY", "newSlot", "LUNCH", "weekStart", w.toString())
                .andExpect(redirectedUrl("/meal-plan?week=" + w));

        assertThat(entries(w))
                .extracting(e -> e.getMeal().getName(), MealPlanEntry::getDayOfWeek, MealPlanEntry::getSlot,
                        MealPlanEntry::getServings)
                .contains(tuple("Pasta", DayOfWeek.TUESDAY, MealSlot.DINNER, 2),
                        tuple("Pasta", DayOfWeek.FRIDAY, MealSlot.LUNCH, 2))
                .hasSize(4);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResultActions postCopy(LocalDate source, LocalDate target) throws Exception {
        return submit("/meal-plan/copy-week", referer(target),
                "sourceWeek", source.toString(), "weekStart", target.toString());
    }

    /** A form post from the weekly page, as the browser sends it (session, CSRF token, Referer). */
    private ResultActions submit(String url, String referer, String... params) throws Exception {
        var request = post(url)
                .session(sessionFor(alice)).param("_csrf", maskedCsrf()).header("Referer", referer);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mockMvc.perform(request);
    }

    private static String referer(LocalDate week) {
        return "http://localhost/meal-plan?week=" + week;
    }

    private ResultActions showWeek(LocalDate week) throws Exception {
        return mockMvc.perform(get("/meal-plan").param("week", week.toString()).session(sessionFor(alice)))
                .andExpect(status().isOk());
    }

    /** The weekly page after a redirect, with the flash attributes the redirect carried. */
    private ResultActions showWeek(LocalDate week, FlashMap flashMap) throws Exception {
        return mockMvc.perform(get("/meal-plan").param("week", week.toString())
                        .session(sessionFor(alice)).flashAttrs(flashMap))
                .andExpect(status().isOk());
    }

    private List<MealPlanEntry> entries(LocalDate week) {
        return tx.execute(s -> mealPlans.findByWeekStartAndHouseholdId(week, household.getId())
                .map(plan -> List.copyOf(plan.getEntries()))
                .orElse(List.of()));
    }

    private int planCount() {
        return jdbc.queryForObject("select count(*) from meal_plans", Integer.class);
    }

    private Meal meal(String name, boolean archived) {
        return meals.save(Meal.builder().name(name).category(MealCategory.MAIN_COURSE).servings(2)
                .archived(archived).owner(alice).household(household).build());
    }

    private MealPlan plan(LocalDate monday, boolean frozen, MealPlanEntry... entries) {
        MealPlan plan = MealPlan.builder().name("Week of " + monday).weekStart(monday).frozen(frozen)
                .owner(alice).household(household).build();
        for (MealPlanEntry e : entries) plan.addEntry(e);
        return mealPlans.save(plan);
    }

    private static MealPlanEntry entry(DayOfWeek day, MealSlot slot, Meal meal, int servings, boolean prepared) {
        return MealPlanEntry.builder().dayOfWeek(day).slot(slot).meal(meal).servings(servings)
                .prepared(prepared).build();
    }
}
