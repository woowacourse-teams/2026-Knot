package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentConfirmationItemResult;
import com.knot.backend.document.domain.DocumentConfirmationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DocumentConfirmationItemResponse(
        @Schema(description = "생성 당시 확인 대상 Member ID", requiredMode = REQUIRED) long memberId,
        @Schema(description = "대상의 현재 닉네임", requiredMode = REQUIRED) String nickname,
        @Schema(description = "현재 프로필 이미지 URL. 없으면 null", nullable = true, requiredMode = REQUIRED) String profileImageUrl,
        @Schema(description = "최초 확인 시각, UTC. 미확인이면 null", nullable = true, requiredMode = REQUIRED) Instant confirmedAt,
        @Schema(description = "CONFIRMED=확인 완료, PENDING=활성 미확인 대상, EXCLUDED=탈퇴한 미확인 대상", requiredMode = REQUIRED) DocumentConfirmationState state
) {
    public static DocumentConfirmationItemResponse from(DocumentConfirmationItemResult result) {
        return new DocumentConfirmationItemResponse(
                result.memberId(),
                result.nickname(),
                result.profileImageUrl(),
                result.confirmedAt(),
                result.state()
        );
    }
}
