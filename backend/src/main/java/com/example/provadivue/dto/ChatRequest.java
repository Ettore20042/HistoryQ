package com.example.provadivue.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "La domanda non può essere vuota") String question
) {
}
