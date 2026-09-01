package com.knot.backend.workspace.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notion Import 시작 결과")
public record NotionImportStartResponse(@Schema(description = "Import 실행 ID", example = "1") long id) {
}
