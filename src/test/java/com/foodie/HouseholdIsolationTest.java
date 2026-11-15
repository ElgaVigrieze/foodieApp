package com.foodie;

import com.foodie.model.MealCategory;
import com.foodie.model.MealSlot;
import com.foodie.model.Unit;
import com.foodie.model.WorkoutType;
import com.foodie.model.db.*;
import static com.foodie.support.TestSessions.maskedCsrf;
import static com.foodie.support.TestSessions.sessionFor;
import com.foodie.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.foodie.support.PostgresTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tenant-isolation tests: a user in household B must not be able to read or modify
 * anything that belongs to household A, through any controller endpoint.
 *
 * <p>Alice (household A) owns one of everything. Bob (household B) attacks it. Every
 * cross-household request must be answered with 403 AND leave Alice's data untouched.
 * A positive control confirms Alice herself is still allowed.
 */
@SpringBootTest
@Import(PostgresTestConfig.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HouseholdIsolationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9); // a Monday
    private static final String ALICE_MEAL = "AliceSecretStew";
    private static final String ALICE_PRODUCT = "AliceSecretFlour";
    private static final String ALICE_WORKOUT = "AliceSecretWorkout";

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired MockMvc mockMvc;
    @Autowired TransactionTemplate tx;

    @Autowired AppUserRepository users;
    @Autowired HouseholdRepository households;
    @Autowired MealRepository meals;
    @Autowired ProductRepository products;
    @Autowired FoodLogRepository foodLogs;
    @Autowired MealPlanRepository mealPlans;
    @Autowired ShoppingListItemRepository shoppingItems;
    @Autowired WorkoutRepository workouts;
    @Autowired TrainingPlanEntryRepository planEntries;
    @Autowired BrainTestResultRepository brainResults;

    AppUser alice;
    AppUser bob;
    Meal aliceMeal;
    Product aliceProduct;
    FoodLog aliceLog;
    MealPlan alicePlan;
    Long alicePlanEntryId;
    ShoppingListItem aliceShoppingItem;
    Workout aliceWorkout;
    TrainingPlanEntry alicePlanWorkoutEntry;

    @BeforeEach
    void setUp() {
        com.foodie.support.DatabaseCleaner.clean(jdbc);
        shoppingItems.deleteAll();
        planEntries.deleteAll();
        workouts.deleteAll();
        foodLogs.deleteAll();
        mealPlans.deleteAll();
        meals.deleteAll();
        products.deleteAll();
        brainResults.deleteAll();
        users.deleteAll();
        households.deleteAll();

        Household householdA = households.save(Household.builder().name("A").build());
        Household householdB = households.save(Household.builder().name("B").build());

        alice = users.save(AppUser.builder().googleId("g-alice").email("alice@example.com")
                .name("Alice").household(householdA).build());
        bob = users.save(AppUser.builder().googleId("g-bob").email("bob@example.com")
                .name("Bob").household(householdB).build());

        aliceProduct = products.save(Product.builder().name(ALICE_PRODUCT).unit(Unit.KG)
                .pricePerUnit(BigDecimal.ONE).calories(BigDecimal.TEN).household(householdA).build());

        aliceMeal = meals.save(Meal.builder().name(ALICE_MEAL).category(MealCategory.MAIN_COURSE)
                .servings(2).owner(alice).household(householdA).build());

        aliceLog = foodLogs.save(FoodLog.builder().date(DAY).slot(MealSlot.LUNCH).meal(aliceMeal)
                .servingsConsumed(BigDecimal.ONE).owner(alice).household(householdA).build());

        alicePlan = MealPlan.builder().weekStart(DAY).owner(alice).household(householdA).build();
        alicePlan.addEntry(MealPlanEntry.builder().dayOfWeek(DayOfWeek.MONDAY).slot(MealSlot.DINNER)
                .meal(aliceMeal).servings(1).build());
        alicePlan = mealPlans.save(alicePlan);
        alicePlanEntryId = tx.execute(s -> mealPlans.findById(alicePlan.getId()).orElseThrow()
                .getEntries().get(0).getId());

        aliceShoppingItem = shoppingItems.save(ShoppingListItem.builder().weekStart(DAY)
                .product(aliceProduct).calculatedQuantity(BigDecimal.ONE)
                .adjustedQuantity(BigDecimal.ONE).owner(alice).household(householdA).build());

        aliceWorkout = workouts.save(Workout.builder().name(ALICE_WORKOUT).type(WorkoutType.RUNNING)
                .owner(alice).build());
        alicePlanWorkoutEntry = planEntries.save(TrainingPlanEntry.builder().date(DAY)
                .workout(aliceWorkout).owner(alice).build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────


    private ResultActions bobPosts(String url, String... params) throws Exception {
        return mockMvc.perform(withParams(post(url), params).session(sessionFor(bob)).param("_csrf", maskedCsrf()));
    }

    private ResultActions bobGets(String url, String... params) throws Exception {
        return mockMvc.perform(withParams(get(url), params).session(sessionFor(bob)));
    }

    private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder b, String... kv) {
        for (int i = 0; i < kv.length; i += 2) {
            b.param(kv[i], kv[i + 1]);
        }
        return b;
    }

    // ── Positive controls: the owner is still allowed ────────────────────────

    @Test
    void ownerCanViewOwnMeal() throws Exception {
        mockMvc.perform(get("/meals/" + aliceMeal.getId()).session(sessionFor(alice)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ALICE_MEAL)));
    }

    @Test
    void ownerCanToggleOwnFavorite() throws Exception {
        mockMvc.perform(post("/meals/" + aliceMeal.getId() + "/toggle-favorite")
                        .session(sessionFor(alice)).param("_csrf", maskedCsrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(meals.findById(aliceMeal.getId()).orElseThrow().isFavorite()).isTrue();
    }

    @Test
    void unauthenticatedUserIsSentToLogin() throws Exception {
        mockMvc.perform(get("/meals"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ── Reads: Bob must not see Alice's data ─────────────────────────────────

    @Test
    void bobCannotOpenAlicesMeal() throws Exception {
        bobGets("/meals/" + aliceMeal.getId()).andExpect(status().isForbidden());
        bobGets("/meals/" + aliceMeal.getId() + "/edit").andExpect(status().isForbidden());
        bobGets("/meals/" + aliceMeal.getId() + "/copy").andExpect(status().isForbidden());
    }

    @Test
    void bobCannotOpenAlicesProduct() throws Exception {
        bobGets("/products/" + aliceProduct.getId() + "/edit").andExpect(status().isForbidden());
    }

    @Test
    void bobCannotOpenAlicesWorkout() throws Exception {
        bobGets("/fitness/workouts/" + aliceWorkout.getId()).andExpect(status().isForbidden());
        bobGets("/fitness/workouts/" + aliceWorkout.getId() + "/edit").andExpect(status().isForbidden());
    }

    @Test
    void bobsMealListDoesNotShowAlicesMeals() throws Exception {
        bobGets("/meals").andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ALICE_MEAL))));
    }

    @Test
    void bobsProductListDoesNotShowAlicesProducts() throws Exception {
        bobGets("/products").andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ALICE_PRODUCT))));
    }

    @Test
    void bobsFoodLogDoesNotShowAlicesEntries() throws Exception {
        bobGets("/log", "date", DAY.toString()).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ALICE_MEAL))));
    }

    @Test
    void bobsMealPlanDoesNotShowAlicesPlan() throws Exception {
        bobGets("/meal-plan", "week", DAY.toString()).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ALICE_MEAL))));
    }

    @Test
    void bobsShoppingListDoesNotShowAlicesItems() throws Exception {
        bobGets("/shopping-list", "week", DAY.toString()).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ALICE_PRODUCT))));
    }

    @Test
    void bobsWorkoutListDoesNotShowAlicesWorkouts() throws Exception {
        bobGets("/fitness/workouts").andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ALICE_WORKOUT))));
    }

    // ── Writes: Bob must not change or delete Alice's data ───────────────────

    @Test
    void bobCannotModifyAlicesMeal() throws Exception {
        String id = String.valueOf(aliceMeal.getId());
        bobPosts("/meals/" + id + "/toggle-favorite").andExpect(status().isForbidden());
        bobPosts("/meals/" + id + "/toggle-archive").andExpect(status().isForbidden());
        bobPosts("/meals/" + id + "/delete").andExpect(status().isForbidden());

        Meal after = meals.findById(aliceMeal.getId()).orElseThrow();
        assertThat(after.isFavorite()).isFalse();
        assertThat(after.isArchived()).isFalse();
    }

    @Test
    void bobCannotDeleteAlicesProduct() throws Exception {
        bobPosts("/products/" + aliceProduct.getId() + "/delete").andExpect(status().isForbidden());
        assertThat(products.existsById(aliceProduct.getId())).isTrue();
    }

    @Test
    void bobCannotModifyAlicesFoodLogEntries() throws Exception {
        String id = String.valueOf(aliceLog.getId());
        bobPosts("/log/" + id + "/update-servings", "servings", "9", "date", DAY.toString())
                .andExpect(status().isForbidden());
        bobPosts("/log/" + id + "/update-quantity", "quantity", "9", "date", DAY.toString())
                .andExpect(status().isForbidden());
        bobPosts("/log/" + id + "/delete", "date", DAY.toString())
                .andExpect(status().isForbidden());

        FoodLog after = foodLogs.findById(aliceLog.getId()).orElseThrow();
        assertThat(after.getServingsConsumed()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void bobCannotRemoveEntriesFromAlicesMealPlan() throws Exception {
        bobPosts("/meal-plan/remove", "planId", String.valueOf(alicePlan.getId()),
                "entryId", String.valueOf(alicePlanEntryId), "weekStart", DAY.toString())
                .andExpect(status().isForbidden());
        bobPosts("/meal-plan/toggle-freeze", "planId", String.valueOf(alicePlan.getId()),
                "weekStart", DAY.toString())
                .andExpect(status().isForbidden());

        int entryCount = tx.execute(s -> mealPlans.findById(alicePlan.getId()).orElseThrow().getEntries().size());
        assertThat(entryCount).isEqualTo(1);
        assertThat(mealPlans.findById(alicePlan.getId()).orElseThrow().isFrozen()).isFalse();
    }

    @Test
    void bobCannotModifyAlicesShoppingItems() throws Exception {
        String id = String.valueOf(aliceShoppingItem.getId());
        String week = DAY.toString();
        bobPosts("/shopping-list/toggle-checked", "itemId", id, "weekStart", week).andExpect(status().isForbidden());
        bobPosts("/shopping-list/update-quantity", "itemId", id, "quantity", "99", "weekStart", week)
                .andExpect(status().isForbidden());
        bobPosts("/shopping-list/update-comment", "itemId", id, "comment", "hacked", "weekStart", week)
                .andExpect(status().isForbidden());
        bobPosts("/shopping-list/remove-item", "itemId", id, "weekStart", week).andExpect(status().isForbidden());

        ShoppingListItem after = shoppingItems.findById(aliceShoppingItem.getId()).orElseThrow();
        assertThat(after.isChecked()).isFalse();
        assertThat(after.getAdjustedQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(after.getComment()).isNull();
    }

    @Test
    void bobCannotModifyAlicesWorkoutsOrTrainingPlan() throws Exception {
        String week = DAY.toString();
        bobPosts("/fitness/plan/toggle/" + alicePlanWorkoutEntry.getId(), "weekStart", week)
                .andExpect(status().isForbidden());
        bobPosts("/fitness/plan/remove/" + alicePlanWorkoutEntry.getId(), "weekStart", week)
                .andExpect(status().isForbidden());
        bobPosts("/fitness/workouts/" + aliceWorkout.getId() + "/delete")
                .andExpect(status().isForbidden());

        assertThat(workouts.existsById(aliceWorkout.getId())).isTrue();
        TrainingPlanEntry after = planEntries.findById(alicePlanWorkoutEntry.getId()).orElseThrow();
        assertThat(after.isCompleted()).isFalse();
    }

    // ── Cross-references: Bob must not pull Alice's records into his own data ─

    @Test
    void bobCannotLogAlicesMealOrProduct() throws Exception {
        bobPosts("/log/add", "date", DAY.toString(), "mealId", String.valueOf(aliceMeal.getId()),
                "servingsConsumed", "1", "slot", "LUNCH").andExpect(status().isForbidden());
        bobPosts("/log/add-product", "date", DAY.toString(), "productId", String.valueOf(aliceProduct.getId()),
                "quantity", "100", "slot", "LUNCH").andExpect(status().isForbidden());

        assertThat(foodLogs.findByDateAndOwnerIdOrderByIdAsc(DAY, bob.getId())).isEmpty();
    }

    @Test
    void bobCannotPlanAlicesMeal() throws Exception {
        bobPosts("/meal-plan/add", "weekStart", DAY.toString(), "day", "MONDAY", "slot", "DINNER",
                "mealId", String.valueOf(aliceMeal.getId()), "servings", "1")
                .andExpect(status().isForbidden());

        assertThat(mealPlans.findByHouseholdId(bob.getHousehold().getId()).stream()
                .flatMap(p -> tx.execute(s -> mealPlans.findById(p.getId()).orElseThrow().getEntries()).stream()))
                .isEmpty();
    }

    @Test
    void bobCannotAssignAlicesWorkoutToHisPlan() throws Exception {
        bobPosts("/fitness/plan/assign", "workoutId", String.valueOf(aliceWorkout.getId()),
                "date", DAY.toString(), "weekStart", DAY.toString())
                .andExpect(status().isForbidden());

        assertThat(planEntries.findAll()).extracting(e -> e.getOwner().getId()).containsOnly(alice.getId());
    }

    @Test
    void bobCannotAddAlicesProductToHisShoppingList() throws Exception {
        bobPosts("/shopping-list/add-item", "weekStart", DAY.toString(),
                "productId", String.valueOf(aliceProduct.getId()), "quantity", "1")
                .andExpect(status().isForbidden());

        assertThat(shoppingItems.count()).isEqualTo(1); // still only Alice's item
    }

    // ── Fixes covered: owners keep access, ingredients, users without a household ─

    @Test
    void ownerCanStillUseOwnShoppingListAndWorkouts() throws Exception {
        mockMvc.perform(post("/shopping-list/toggle-checked")
                        .session(sessionFor(alice)).param("_csrf", maskedCsrf())
                        .param("itemId", String.valueOf(aliceShoppingItem.getId()))
                        .param("weekStart", DAY.toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(shoppingItems.findById(aliceShoppingItem.getId()).orElseThrow().isChecked()).isTrue();

        mockMvc.perform(get("/shopping-list").param("week", DAY.toString()).session(sessionFor(alice)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ALICE_PRODUCT)));

        mockMvc.perform(get("/fitness/workouts/" + aliceWorkout.getId()).session(sessionFor(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void bobCannotUseAlicesProductAsAMealIngredient() throws Exception {
        bobPosts("/meals/save", "name", "Stolen soup", "category", "SOUP", "servings", "1",
                "productIds", String.valueOf(aliceProduct.getId()), "quantities", "1")
                .andExpect(status().isForbidden());

        assertThat(meals.findAll()).extracting(Meal::getName).containsOnly(ALICE_MEAL);
    }

    // ── Users without a household must create or join one first ──────────────

    private AppUser carolWithoutHousehold() {
        return users.save(AppUser.builder().googleId("g-carol").email("carol@example.com").name("Carol").build());
    }

    @Test
    void userWithoutAHouseholdIsSentToHouseholdSetupFromEveryPage() throws Exception {
        AppUser carol = carolWithoutHousehold();

        for (String page : new String[] {"/meals", "/products", "/meal-plan", "/shopping-list", "/budget", "/log"}) {
            mockMvc.perform(get(page).session(sessionFor(carol)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/household"));
        }
        mockMvc.perform(post("/products/save").session(sessionFor(carol)).param("_csrf", maskedCsrf())
                        .param("name", "CarolRice").param("unit", "KG").param("pricePerUnit", "2"))
                .andExpect(redirectedUrl("/household"));
        assertThat(products.findAll()).extracting(Product::getName).doesNotContain("CarolRice");
    }

    @Test
    void userWithoutAHouseholdGetsForbiddenFromTheApi() throws Exception {
        AppUser carol = carolWithoutHousehold();

        mockMvc.perform(post("/api/barcode/save-product").session(sessionFor(carol)).param("_csrf", maskedCsrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"4001234567890\",\"name\":\"CarolMuesli\",\"calories\":300}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Create or join a household first"));
        assertThat(products.findAll()).extracting(Product::getName).doesNotContain("CarolMuesli");
    }

    @Test
    void userWithoutAHouseholdCanCreateOneAndThenUseTheApp() throws Exception {
        AppUser carol = carolWithoutHousehold();

        mockMvc.perform(get("/household").session(sessionFor(carol))).andExpect(status().isOk());
        mockMvc.perform(post("/household/create").session(sessionFor(carol)).param("_csrf", maskedCsrf())
                        .param("name", "Carol's"))
                .andExpect(redirectedUrl("/household"));

        assertThat(users.findById(carol.getId()).orElseThrow().getHousehold()).isNotNull();
        mockMvc.perform(get("/products").session(sessionFor(carol)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ALICE_PRODUCT))));
    }

    @Test
    void budgetPagesDoNotListOtherHouseholdsWeeks() throws Exception {
        String aliceWeek = "value=\"" + DAY + "\"";

        bobGets("/budget", "week", "2030-01-07").andExpect(status().isOk())
                .andExpect(content().string(not(containsString(aliceWeek))));
        mockMvc.perform(get("/budget").param("week", "2030-01-07").session(sessionFor(alice)))
                .andExpect(content().string(containsString(aliceWeek)));
    }

    @Test
    void newProductsAlwaysGoToTheCreatorsHouseholdAndStayThere() throws Exception {
        bobPosts("/products/save", "name", "BobRice", "unit", "KG", "pricePerUnit", "2")
                .andExpect(status().is3xxRedirection());
        Product bobs = products.findAll().stream().filter(p -> p.getName().equals("BobRice")).findFirst().orElseThrow();
        assertThat(bobs.getHousehold().getId()).isEqualTo(bob.getHousehold().getId());

        mockMvc.perform(get("/products").session(sessionFor(alice)))
                .andExpect(content().string(not(containsString("BobRice"))));
        mockMvc.perform(post("/products/save").session(sessionFor(alice)).param("_csrf", maskedCsrf())
                        .param("id", String.valueOf(bobs.getId())).param("name", "Hijacked")
                        .param("unit", "KG").param("pricePerUnit", "1"))
                .andExpect(status().isForbidden());
        assertThat(products.findById(bobs.getId()).orElseThrow().getName()).isEqualTo("BobRice");
    }

    @Test
    void theDatabaseRejectsAProductWithoutAHousehold() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> products.saveAndFlush(
                        Product.builder().name("Orphan").unit(Unit.KG).pricePerUnit(BigDecimal.ONE).build()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── Shopping list: a product is on a week's list at most once ───────────

    private List<ShoppingListItem> aliceItemsFor(Product product) {
        return shoppingItems.findByWeekStartAndHouseholdIdOrderByProductNameAsc(DAY, alice.getHousehold().getId())
                .stream().filter(i -> i.getProduct().getId().equals(product.getId())).toList();
    }

    @Test
    void addingAProductThatIsAlreadyOnTheListRaisesItsQuantity() throws Exception {
        mockMvc.perform(post("/shopping-list/add-item").session(sessionFor(alice)).param("_csrf", maskedCsrf())
                        .param("weekStart", DAY.toString()).param("productId", String.valueOf(aliceProduct.getId()))
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection());

        List<ShoppingListItem> rows = aliceItemsFor(aliceProduct);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(aliceShoppingItem.getId());
        assertThat(rows.get(0).getAdjustedQuantity()).isEqualByComparingTo("3"); // 1 from before + 2
    }

    @Test
    void regenerateMergesAProductThatIsOnTheListTwice() throws Exception {
        // The plan needs aliceProduct: 2 per meal of 2 servings, planned for 1 serving -> 1.
        tx.executeWithoutResult(s -> {
            Meal meal = meals.findById(aliceMeal.getId()).orElseThrow();
            meal.addIngredient(MealIngredient.builder().product(aliceProduct).quantity(new BigDecimal("2")).build());
            meals.save(meal);
        });
        // A list saved before additions were merged: the plan's row plus a second, hand-added row.
        shoppingItems.save(ShoppingListItem.builder().weekStart(DAY).product(aliceProduct)
                .calculatedQuantity(BigDecimal.ZERO).adjustedQuantity(new BigDecimal("3"))
                .comment("the big pack").owner(alice).household(alice.getHousehold()).build());
        assertThat(aliceItemsFor(aliceProduct)).hasSize(2);

        mockMvc.perform(post("/shopping-list/regenerate").session(sessionFor(alice)).param("_csrf", maskedCsrf())
                        .param("weekStart", DAY.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/shopping-list?week=" + DAY));

        List<ShoppingListItem> rows = aliceItemsFor(aliceProduct);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCalculatedQuantity()).isEqualByComparingTo("1");
        assertThat(rows.get(0).getAdjustedQuantity()).isEqualByComparingTo("4"); // 1 planned + 3 by hand
        assertThat(rows.get(0).getComment()).isEqualTo("the big pack");
    }
}
