import type { ReferencePage } from "../types/searchReference";

const SOURCE_LABEL: Record<string, string> = {
  NOTION: "Notion",
};

const padTwoDigits = (value: number) => String(value).padStart(2, "0");

/**
 * 출처 카드의 두 번째 줄 문구를 만듭니다. 예: `Notion · 2026.09.01 수정`
 *
 * 서버 출처 응답에는 문서의 위치(폴더 경로)가 없어 제공자와 마지막 수정일을 대신 보여 줘요.
 * 수정 시각이 올바른 날짜가 아니면 제공자만 보여 줍니다.
 *
 * @param page - 페이지로 묶인 출처 항목
 * @returns 카드에 보여 줄 위치 문구
 * @example
 * formatReferenceLocation({ source: "NOTION", updatedAt: "2026-09-01T04:12:35Z", ... }); // "Notion · 2026.09.01 수정"
 */
export const formatReferenceLocation = ({
  source,
  updatedAt,
}: Pick<ReferencePage, "source" | "updatedAt">) => {
  const label = SOURCE_LABEL[source] ?? source;
  const updated = new Date(updatedAt);

  if (Number.isNaN(updated.getTime())) return label;

  const date = [
    updated.getFullYear(),
    padTwoDigits(updated.getMonth() + 1),
    padTwoDigits(updated.getDate()),
  ].join(".");

  return `${label} · ${date} 수정`;
};
