package com.knot.backend.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteNicknameRequest(@NotBlank @Size(max = 20) String nickname) {
}
