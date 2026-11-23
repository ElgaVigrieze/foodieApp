package com.foodie.controller.form;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Validation of {@link MealPlanForms.CopyWeek}. */
class MealPlanFormsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void copyWeekWithBothDatesIsValid() {
        var form = new MealPlanForms.CopyWeek(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9));

        assertThat(validator.validate(form)).isEmpty();
        assertThat(form.sourceWeek()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(form.weekStart()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void copyWeekRequiresSourceWeek() {
        var violations = validator.validate(new MealPlanForms.CopyWeek(null, LocalDate.of(2026, 11, 9)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("sourceWeek");
    }

    @Test
    void copyWeekRequiresWeekStart() {
        var violations = validator.validate(new MealPlanForms.CopyWeek(LocalDate.of(2026, 11, 2), null));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("weekStart");
    }
}
