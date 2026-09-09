package com.knot.backend.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "디바이스 코드 교환 요청(기획서 5.2)")
public record DeviceTokenRequest(
        @Schema(description = "브라우저 콜백으로 받은 일회용 코드", example = "Xn8b2…") @NotBlank @Size(
                max = 128
        ) String code,
        @Schema(
                description = "PKCE code_verifier(43~128자). S256 해시가 로그인 시작 시 보낸 code_challenge와 같아야 한다",
                example = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        ) @NotBlank @Size(min = 43, max = 128) String codeVerifier,
        @Valid @NotNull DeviceRequest device
) {
}
