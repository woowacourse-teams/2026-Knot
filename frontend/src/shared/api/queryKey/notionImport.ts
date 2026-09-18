export const notionImportKeys = {
  all: ["notionImports"] as const,
  // 추적할 실행이 없는 동안(null)에도 키가 필요해 null을 허용해요. 그때는 쿼리가 비활성이라 요청은 없어요
  detail: (importRunId: number | null) =>
    [...notionImportKeys.all, "detail", importRunId] as const,
};
