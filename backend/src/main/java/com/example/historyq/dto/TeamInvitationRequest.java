package com.example.historyq.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamInvitationRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {
}