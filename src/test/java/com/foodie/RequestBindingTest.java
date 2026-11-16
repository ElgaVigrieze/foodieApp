package com.foodie;

import com.foodie.model.MealCategory;
import com.foodie.model.Unit;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.Household;
import com.foodie.model.db.Meal;
import com.foodie.model.db.Product;
import com.foodie.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import com.foodie.support.PostgresTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.foodie.support.TestSessions.maskedCsrf;
import static com.foodie.support.TestSessions.sessionFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Request binding and validation: form objects (Thymeleaf forms) and typed JSON bodies.
 * Covers what the typed request objects added on top of loose {@code @RequestParam}s / maps:
 * validation errors, no mass assignment, and the happy paths still binding correctly.
 */
@SpringBootTest
@Import(PostgresTestConfig.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RequestBindingTest {

    private static final String TODAY = LocalDate.of(2026, 11, 9).toString();

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired HouseholdRepository households;
    @Autowired MealRepository meals;
    @Autowired ProductRepository products;
    @Autowired FoodLogRepository foodLogs;
    @Autowired BrainTestResultRepository brainResults;

    Household householdA;
    Household householdB;
    AppUser alice;
    AppUser bob;
    Product aliceProduct;

    @BeforeEach
    void setUp() {
        com.foodie.support.DatabaseCleaner.clean(jdbc);
        foodLogs.deleteAll();
        meals.deleteAll();
        products.deleteAll();
        brainResults.deleteAll();
        users.deleteAll();
        households.deleteAll();

        householdA = households.save(Household.builder().name("A").build());
        householdB = households.save(Household.builder().name("B").build());
        alice = users.save(AppUser.builder().googleId("g-alice").email("alice@example.com")
                .name("Alice").household(householdA).build());
        bob = users.save(AppUser.builder().googleId("g-bob").email("bob@example.com")
                .name("Bob").household(householdB).build());
        aliceProduct = products.save(Product.builder().name("AliceFlour").unit(Unit.KG)
                .pricePerUnit(BigDecimal.ONE).household(householdA).build());
    }

    private ResultActions form(AppUser user, String url, String... params) throws Exception {
        MockHttpServletRequestBuilder request = post(url).session(sessionFor(user)).param("_csrf", maskedCsrf());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions json(AppUser user, String url, String body) throws Exception {
        return mockMvc.perform(post(url).session(sessionFor(user)).param("_csrf", maskedCsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ── Product form: no mass assignment ─────────────────────────────────────

    @Test
    void ownerCanCreateAndEditProduct() throws Exception {
        form(alice, "/products/save", "name", "Oats", "unit", "KG", "pricePerUnit", "2.50", "calories", "380")
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/products"));

        Product created = products.findAll().stream().filter(p -> p.getName().equals("Oats")).findFirst().orElseThrow();
        assertThat(created.getHousehold().getId()).isEqualTo(householdA.getId());

        form(alice, "/products/save", "id", String.valueOf(created.getId()), "name", "Oats v2",
                "unit", "KG", "pricePerUnit", "3.00")
                .andExpect(status().is3xxRedirection());
        assertThat(products.findById(created.getId()).orElseThrow().getName()).isEqualTo("Oats v2");
    }

    @Test
    void bobCannotOverwriteAlicesProductByPostingItsId() throws Exception {
        form(bob, "/products/save", "id", String.valueOf(aliceProduct.getId()), "name", "Hijacked",
                "unit", "KG", "pricePerUnit", "1")
                .andExpect(status().isForbidden());

        assertThat(products.findById(aliceProduct.getId()).orElseThrow().getName()).isEqualTo("AliceFlour");
    }

    @Test
    void postingAHouseholdIdCannotMoveAProductIntoAnotherHousehold() throws Exception {
        form(bob, "/products/save", "name", "Sneaky", "unit", "KG", "pricePerUnit", "1",
                "household.id", String.valueOf(householdA.getId()))
                .andExpect(status().is3xxRedirection());

        Product saved = products.findAll().stream().filter(p -> p.getName().equals("Sneaky")).findFirst().orElseThrow();
        assertThat(saved.getHousehold().getId()).isEqualTo(householdB.getId());
    }

    @Test
    void invalidProductFormIsRedisplayedWithoutServerError() throws Exception {
        form(alice, "/products/save", "name", "", "unit", "KG", "pricePerUnit", "-5")
                .andExpect(status().isOk())
                .andExpect(view().name("products/form"));
        assertThat(products.count()).isEqualTo(1); // only Alice's seeded product
    }

    // ── Meal form ────────────────────────────────────────────────────────────

    @Test
    void mealFormBindsIngredientLists() throws Exception {
        form(alice, "/meals/save", "name", "Porridge", "category", "MAIN_COURSE", "servings", "2",
                "productIds", String.valueOf(aliceProduct.getId()), "quantities", "0.5")
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/meals"));

        Meal meal = meals.findAll().get(0);
        assertThat(meal.getName()).isEqualTo("Porridge");
        assertThat(meal.getCategory()).isEqualTo(MealCategory.MAIN_COURSE);
        assertThat(meal.getServings()).isEqualTo(2);
    }

    @Test
    void mealWithoutIngredientsGetsFriendlyMessage() throws Exception {
        form(alice, "/meals/save", "name", "Empty", "category", "SOUP", "servings", "1")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Please add at least one ingredient."));
        assertThat(meals.count()).isZero();
    }


    @Test
    void mealWithFewerQuantitiesThanProductsIsRejectedNotA500() throws Exception {
        form(alice, "/meals/save", "name", "Mismatch", "category", "SOUP", "servings", "1",
                "productIds", String.valueOf(aliceProduct.getId()), "productIds", String.valueOf(aliceProduct.getId()),
                "quantities", "1")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertThat(meals.count()).isZero();
    }
    @Test
    void invalidMealFormIsRejectedBeforeReachingTheService() throws Exception {
        form(alice, "/meals/save", "name", " ", "category", "SOUP", "servings", "0",
                "productIds", String.valueOf(aliceProduct.getId()), "quantities", "1")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertThat(meals.count()).isZero();
    }

    // ── Other form validation ────────────────────────────────────────────────

    @Test
    void negativeQuantityIsRejectedWithAFlashMessage() throws Exception {
        form(alice, "/log/add-product", "date", TODAY, "productId", String.valueOf(aliceProduct.getId()),
                "quantity", "-5", "slot", "LUNCH")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertThat(foodLogs.count()).isZero();
    }

    @Test
    void malformedDateIsRejectedNotServerError() throws Exception {
        form(alice, "/log/add-product", "date", "not-a-date", "productId", String.valueOf(aliceProduct.getId()),
                "quantity", "5")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void validFoodLogProductEntryStillWorksAndSlotIsOptional() throws Exception {
        form(alice, "/log/add-product", "date", TODAY, "productId", String.valueOf(aliceProduct.getId()),
                "quantity", "150")
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/log?date=" + TODAY));
        assertThat(foodLogs.count()).isEqualTo(1);
    }

    // ── JSON bodies ──────────────────────────────────────────────────────────

    @Test
    void photoEntryAcceptsEmptySlotString() throws Exception {
        json(alice, "/api/photo/save", """
                {"date":"%s","description":"toast","calories":250,"protein":8,"carbs":40,"fat":5,"fiber":3,"slot":""}
                """.formatted(TODAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(foodLogs.count()).isEqualTo(1);
    }

    @Test
    void photoEntryWithoutDateIsA400WithJsonError() throws Exception {
        json(alice, "/api/photo/save", """
                {"description":"toast","calories":250}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("date")));
    }

    @Test
    void photoEntryWithNegativeCaloriesIsRejected() throws Exception {
        json(alice, "/api/photo/save", """
                {"date":"%s","calories":-1}
                """.formatted(TODAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("calories")));
        assertThat(foodLogs.count()).isZero();
    }

    @Test
    void photoEntryWithMalformedDateIsA400NotA500() throws Exception {
        json(alice, "/api/photo/save", """
                {"date":"yesterday","calories":10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void voiceSubmitRequiresTypeAndDate() throws Exception {
        json(alice, "/api/voice/submit", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void voiceSubmitLogsAProduct() throws Exception {
        json(alice, "/api/voice/submit", """
                {"type":"product","date":"%s","productId":%d,"quantity":"200","slot":null}
                """.formatted(TODAY, aliceProduct.getId()))
                .andExpect(status().isOk());
        assertThat(foodLogs.count()).isEqualTo(1);
    }

    @Test
    void barcodeLookupRejectsBlankBarcode() throws Exception {
        json(alice, "/api/barcode/lookup", """
                {"barcode":"   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("barcode")));
    }

    @Test
    void barcodeSaveRejectsMissingNameAndNegativeNutrients() throws Exception {
        json(alice, "/api/barcode/save-product", """
                {"barcode":"123","calories":10}
                """).andExpect(status().isBadRequest());
        json(alice, "/api/barcode/save-product", """
                {"barcode":"123","name":"Bar","calories":-10}
                """).andExpect(status().isBadRequest());
        assertThat(products.count()).isEqualTo(1);
    }

    @Test
    void barcodeSaveCreatesProductInTheUsersHousehold() throws Exception {
        json(alice, "/api/barcode/save-product", """
                {"barcode":"4001234567890","name":"Muesli","calories":360,"protein":9,"carbs":62,"fat":7,"fiber":8,"sugar":12}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        Product saved = products.findAll().stream().filter(p -> "Muesli".equals(p.getName())).findFirst().orElseThrow();
        assertThat(saved.getHousehold().getId()).isEqualTo(householdA.getId());
    }
}
