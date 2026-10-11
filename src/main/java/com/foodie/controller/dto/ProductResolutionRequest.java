package com.foodie.controller.dto;

import java.util.List;

/**
 * Request for resolving product IDs during recipe import confirmation.
 */
public record ProductResolutionRequest(
        List<Long> productIds,
        List<String> productNames,
        List<String> productUnits
) {}
