package com.bench;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateWidgetRequest(
        @NotBlank String name,
        @Positive int quantity) {
}
