package com.foodie.service;

import com.foodie.exception.BadRequestException;
import com.foodie.exception.ConflictException;
import com.foodie.exception.ForbiddenException;
import com.foodie.model.MealSlot;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.Household;
import com.foodie.model.db.Meal;
import com.foodie.model.db.MealPlan;
import com.foodie.model.db.MealPlanEntry;
import com.foodie.repository.MealPlanRepository;
import com.foodie.repository.MealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for {@link MealPlanService#copyWeek} and {@link MealPlanService.CopyResult}. */
@ExtendWith(MockitoExtension.class)
class MealPlanServiceTest {

    private static final LocalDate W = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    @Mock MealPlanRepository mealPlanRepository;
    @Mock MealRepository mealRepository;
    @Mock CurrentUserService currentUserService;
    @InjectMocks MealPlanService service;

    Household household;
    AppUser user;
    Meal chili, pasta, oldStew;

    @BeforeEach
    void setUp() {
        household = Household.builder().id(1L).name("A").build();
        user = AppUser.builder().id(1L).name("Alice").household(household).build();
        lenient().when(currentUserService.requireCurrentHousehold()).thenReturn(household);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(mealPlanRepository.save(any(MealPlan.class))).thenAnswer(i -> i.getArgument(0));

        chili = meal(1L, "Chili", false);
        pasta = meal(2L, "Pasta", false);
        oldStew = meal(3L, "Old stew", true);
    }

    // ── CopyResult.message ───────────────────────────────────────────────────

    @Nested
    class MessageText {
        @Test
        void pluralEntriesWithoutExtras() {
            assertThat(new MealPlanService.CopyResult(3, 0, 0).message()).isEqualTo("Copied 3 entries");
        }

        @Test
        void singularEntry() {
            assertThat(new MealPlanService.CopyResult(1, 0, 0).message()).isEqualTo("Copied 1 entry");
        }

        @Test
        void zeroIsPlural() {
            assertThat(new MealPlanService.CopyResult(0, 0, 0).message()).isEqualTo("Copied 0 entries");
        }

        @Test
        void skippedAndArchivedPartsInOrderAndSingularArchived() {
            assertThat(new MealPlanService.CopyResult(11, 1, 1).message())
                    .isEqualTo("Copied 11 entries (1 skipped as duplicates, 1 archived meal not copied).");
        }

        @Test
        void onlyPositivePartsAreShown() {
            assertThat(new MealPlanService.CopyResult(1, 2, 0).message())
                    .isEqualTo("Copied 1 entry (2 skipped as duplicates).");
            assertThat(new MealPlanService.CopyResult(12, 0, 2).message())
                    .isEqualTo("Copied 12 entries (2 archived meals not copied).");
        }
    }

    // ── Refusals (R2-R6, R11, R12) ───────────────────────────────────────────

    @Nested
    class Refusals {
        @Test
        void sameWeekIsRefused() {
            assertThatThrownBy(() -> service.copyWeek(W, W))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Choose a different week to copy from.");
        }

        @Test
        void sameWeekDifferentDaysIsRefusedAfterSnappingToMonday() {
            assertThatThrownBy(() -> service.copyWeek(W.plusDays(1), W.plusDays(4)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Choose a different week to copy from.");
        }

        @Test
        void sourceAfterCurrentWeekIsRefused() {
            assertThatThrownBy(() -> service.copyWeek(W.plusWeeks(1), W.plusWeeks(2)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("You can only copy from the current week or an earlier week.");
        }

        @Test
        void targetBeforeCurrentWeekIsRefused() {
            assertThatThrownBy(() -> service.copyWeek(W.minusWeeks(1), W.minusWeeks(2)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("You can only copy into the current week or a later week.");
        }

        @Test
        void sourceTooNewIsCheckedBeforeNothingToCopy() {
            // W+1 has no plan at all, but R3 wins over R5
            assertThatThrownBy(() -> service.copyWeek(W.plusWeeks(1), W.plusWeeks(2)))
                    .hasMessage("You can only copy from the current week or an earlier week.");
        }

        @Test
        void missingSourcePlanIsRefusedAndNothingIsCreated() {
            when(mealPlanRepository.findByWeekStartAndHouseholdId(W.minusWeeks(7), 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.copyWeek(W.minusWeeks(7), W.plusWeeks(1)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nothing to copy from that week.");
            verify(mealPlanRepository, never()).save(any());
        }

        @Test
        void emptySourcePlanIsRefused() {
            givenPlan(W.minusWeeks(2), false);

            assertThatThrownBy(() -> service.copyWeek(W.minusWeeks(2), W.plusWeeks(1)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nothing to copy from that week.");
        }

        @Test
        void sourceWithOnlyArchivedMealsIsRefused() {
            givenPlan(W.minusWeeks(1), false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, oldStew, 2));

            assertThatThrownBy(() -> service.copyWeek(W.minusWeeks(1), W.plusWeeks(1)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nothing to copy from that week.");
            verify(mealPlanRepository, never()).save(any());
        }

        @Test
        void frozenTargetIsRefusedWithoutWriting() {
            givenPlan(W, false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2));
            givenPlan(W.plusWeeks(3), true);

            assertThatThrownBy(() -> service.copyWeek(W, W.plusWeeks(3)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("The target week is frozen. Unfreeze it first.");
            verify(mealPlanRepository, never()).save(any());
        }

        @Test
        void nothingToCopyIsReportedBeforeFrozenTarget() {
            givenPlan(W.minusWeeks(2), false);
            givenPlan(W.plusWeeks(3), true);

            assertThatThrownBy(() -> service.copyWeek(W.minusWeeks(2), W.plusWeeks(3)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nothing to copy from that week.");
        }

        @Test
        void userWithoutHouseholdIsForbidden() {
            when(currentUserService.requireCurrentHousehold())
                    .thenThrow(new ForbiddenException("Create or join a household first"));

            assertThatThrownBy(() -> service.copyWeek(W, W.plusWeeks(1)))
                    .isInstanceOf(ForbiddenException.class);
            verify(mealPlanRepository, never()).save(any());
        }
    }

    // ── Copying (R7-R10, R13) ────────────────────────────────────────────────

    @Nested
    class Copying {
        @Test
        void copiesIntoExistingEmptyTargetAndResetsPrepared() {
            MealPlanEntry prepared = entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 4);
            prepared.setPrepared(true);
            givenPlan(W, false, prepared);
            MealPlan target = givenPlan(W.plusWeeks(1), false);

            var result = service.copyWeek(W, W.plusWeeks(1));

            assertThat(result).isEqualTo(new MealPlanService.CopyResult(1, 0, 0));
            assertThat(target.getEntries()).hasSize(1);
            MealPlanEntry copy = target.getEntries().get(0);
            assertThat(copy).isNotSameAs(prepared);
            assertThat(copy.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(copy.getSlot()).isEqualTo(MealSlot.DINNER);
            assertThat(copy.getMeal()).isSameAs(chili);
            assertThat(copy.getServings()).isEqualTo(4);
            assertThat(copy.isPrepared()).isFalse();
            assertThat(copy.getMealPlan()).isSameAs(target);
            verify(mealPlanRepository).save(target);
        }

        @Test
        void nullServingsBecomeOne() {
            givenPlan(W, false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, null));
            MealPlan target = givenPlan(W.plusWeeks(1), false);

            service.copyWeek(W, W.plusWeeks(1));

            assertThat(target.getEntries().get(0).getServings()).isEqualTo(1);
        }

        @Test
        void createsTargetPlanWhenMissing() {
            givenPlan(W, false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2));
            when(mealPlanRepository.findByWeekStartAndHouseholdId(W.plusWeeks(4), 1L)).thenReturn(Optional.empty());

            var result = service.copyWeek(W, W.plusWeeks(4));

            assertThat(result.copied()).isEqualTo(1);
            var captor = org.mockito.ArgumentCaptor.forClass(MealPlan.class);
            verify(mealPlanRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            MealPlan created = captor.getAllValues().get(0);
            assertThat(created.getName()).isEqualTo("Week of " + W.plusWeeks(4));
            assertThat(created.getWeekStart()).isEqualTo(W.plusWeeks(4));
            assertThat(created.getOwner()).isSameAs(user);
            assertThat(created.getHousehold()).isSameAs(household);
        }

        @Test
        void datesSnapToMonday() {
            givenPlan(W, false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2));
            MealPlan target = givenPlan(W.plusWeeks(1), false);

            var result = service.copyWeek(W.plusDays(2), W.plusWeeks(1).plusDays(2));

            assertThat(result.copied()).isEqualTo(1);
            assertThat(target.getEntries()).hasSize(1);
        }

        @Test
        void duplicateIgnoresServingsAndPreparedAndExistingWins() {
            givenPlan(W.minusWeeks(1), false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 4));
            MealPlan target = givenPlan(W, false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 24));

            var result = service.copyWeek(W.minusWeeks(1), W);

            assertThat(result).isEqualTo(new MealPlanService.CopyResult(0, 1, 0));
            assertThat(target.getEntries()).hasSize(1);
            assertThat(target.getEntries().get(0).getServings()).isEqualTo(24);
        }

        @Test
        void sameSlotDifferentMealIsNotADuplicate() {
            givenPlan(W.minusWeeks(1), false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2));
            MealPlan target = givenPlan(W, false, entry(DayOfWeek.MONDAY, MealSlot.DINNER, pasta, 2));

            var result = service.copyWeek(W.minusWeeks(1), W);

            assertThat(result).isEqualTo(new MealPlanService.CopyResult(1, 0, 0));
            assertThat(target.getEntries()).hasSize(2);
        }

        @Test
        void sameMealDifferentDayOrSlotIsNotADuplicate() {
            givenPlan(W.minusWeeks(1), false,
                    entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2),
                    entry(DayOfWeek.MONDAY, MealSlot.LUNCH, chili, 2));
            MealPlan target = givenPlan(W, false, entry(DayOfWeek.TUESDAY, MealSlot.DINNER, chili, 2));

            var result = service.copyWeek(W.minusWeeks(1), W);

            assertThat(result.copied()).isEqualTo(2);
            assertThat(target.getEntries()).hasSize(3);
        }

        @Test
        void identicalEntriesInsideSourceAreBothCopied() {
            givenPlan(W, false,
                    entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2),
                    entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 3));
            MealPlan target = givenPlan(W.plusWeeks(1), false);

            var result = service.copyWeek(W, W.plusWeeks(1));

            assertThat(result.copied()).isEqualTo(2);
            assertThat(target.getEntries()).hasSize(2);
        }

        @Test
        void archivedEntriesAreCountedAndNotCopied() {
            givenPlan(W, false,
                    entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2),
                    entry(DayOfWeek.TUESDAY, MealSlot.DINNER, oldStew, 2));
            MealPlan target = givenPlan(W.plusWeeks(1), false);

            var result = service.copyWeek(W, W.plusWeeks(1));

            assertThat(result).isEqualTo(new MealPlanService.CopyResult(1, 0, 1));
            assertThat(target.getEntries()).extracting(MealPlanEntry::getMeal).containsExactly(chili);
        }

        @Test
        void archivedEntryAlreadyInTargetIsArchivedNotSkipped() {
            givenPlan(W, false,
                    entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2),
                    entry(DayOfWeek.WEDNESDAY, MealSlot.DINNER, oldStew, 2));
            MealPlan target = givenPlan(W.plusWeeks(2), false,
                    entry(DayOfWeek.WEDNESDAY, MealSlot.DINNER, oldStew, 2));

            var result = service.copyWeek(W, W.plusWeeks(2));

            assertThat(result).isEqualTo(new MealPlanService.CopyResult(1, 0, 1));
            assertThat(target.getEntries()).hasSize(2);
        }

        @Test
        void sourcePlanIsNeverModified() {
            MealPlanEntry original = entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 4);
            original.setPrepared(true);
            MealPlan source = givenPlan(W, false, original, entry(DayOfWeek.TUESDAY, MealSlot.DINNER, oldStew, 2));
            givenPlan(W.plusWeeks(1), false);

            service.copyWeek(W, W.plusWeeks(1));

            assertThat(source.getEntries()).hasSize(2);
            assertThat(original.isPrepared()).isTrue();
            assertThat(original.getMealPlan()).isSameAs(source);
            assertThat(source.isFrozen()).isFalse();
            verify(mealPlanRepository, never()).save(source);
        }

        @Test
        void secondRunCopiesNothing() {
            givenPlan(W, false,
                    entry(DayOfWeek.MONDAY, MealSlot.DINNER, chili, 2),
                    entry(DayOfWeek.TUESDAY, MealSlot.DINNER, pasta, 2));
            givenPlan(W.plusWeeks(1), false);

            service.copyWeek(W, W.plusWeeks(1));
            var second = service.copyWeek(W, W.plusWeeks(1));

            assertThat(second).isEqualTo(new MealPlanService.CopyResult(0, 2, 0));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MealPlan givenPlan(LocalDate monday, boolean frozen, MealPlanEntry... entries) {
        MealPlan plan = MealPlan.builder().id(monday.toEpochDay()).name("Week of " + monday)
                .weekStart(monday).frozen(frozen).owner(user).household(household).build();
        for (MealPlanEntry e : entries) plan.addEntry(e);
        lenient().when(mealPlanRepository.findByWeekStartAndHouseholdId(monday, 1L)).thenReturn(Optional.of(plan));
        return plan;
    }

    private static MealPlanEntry entry(DayOfWeek day, MealSlot slot, Meal meal, Integer servings) {
        return MealPlanEntry.builder().dayOfWeek(day).slot(slot).meal(meal).servings(servings).build();
    }

    private static Meal meal(Long id, String name, boolean archived) {
        return Meal.builder().id(id).name(name).servings(2).archived(archived).build();
    }
}
