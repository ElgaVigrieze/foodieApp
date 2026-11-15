package com.foodie.controller.form;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public final class FitnessForms {

    private FitnessForms() {}

    public record AssignWorkout(
            @NotNull Long workoutId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {}
}
