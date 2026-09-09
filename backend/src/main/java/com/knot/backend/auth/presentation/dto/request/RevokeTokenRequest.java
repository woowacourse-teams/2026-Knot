package com.knot.backend.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "토큰 폐기 요청(기획서 5.2, RFC 7009). 본문이 없으면 Bearer 토큰의 세션을 폐기한다")
public record RevokeTokenRequest(
        @Schema(description = "폐기할 리프레시 토큰", nullable = true) @Size(max = 128) String refreshToken
) {
}
