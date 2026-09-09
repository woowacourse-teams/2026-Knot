package com.knot.backend.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "리프레시 토큰 갱신 요청(기획서 5.2)")
public record RefreshTokenRequest(
        @Schema(description = "현재 리프레시 토큰") @NotBlank @Size(max = 128) String refreshToken
) {
}
