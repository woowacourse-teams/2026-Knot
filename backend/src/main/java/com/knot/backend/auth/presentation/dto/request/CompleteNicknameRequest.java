package com.knot.backend.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteNicknameRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[가-힣A-Za-z()-]+$") String nickname
) {
}
