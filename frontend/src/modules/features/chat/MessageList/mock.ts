import type { ChatMessage } from "./types/chatMessage";

/** 백엔드가 붙기 전까지 대화를 그려보기 위한 임시 데이터 */
export const mockMessages: ChatMessage[] = [
  {
    id: 101,
    role: "USER",
    content: "DB 기술 선정 관련해서 정리된 문서 있어?",
    createdAt: "2026-08-31T01:00:00Z",
  },
  {
    id: 102,
    role: "ASSISTANT",
    content:
      "DB는 PostgreSQL로 정해졌어요. 지난주 기술 선정 회의에서 결정됐고, 초기 스키마는 FE와 합의한 범위 안에서만 잡기로 했습니다.",
    createdAt: "2026-08-31T01:00:03Z",
  },
  {
    id: 103,
    role: "USER",
    content: "고마워!",
    createdAt: "2026-08-31T01:01:00Z",
  },
  {
    id: 104,
    role: "ASSISTANT",
    content: "더 궁금한 점이 있으면 언제든 물어보세요.",
    createdAt: "2026-08-31T01:01:02Z",
  },
  {
    id: 105,
    role: "USER",
    content: "그럼 초기 스키마는 어디에 정리돼 있어?",
    createdAt: "2026-08-31T01:02:00Z",
  },
  {
    id: 106,
    role: "ASSISTANT",
    content:
      "초기 스키마는 제품/스펙 아래 ERD 문서에 정리돼 있어요. 회의록에서 합의한 범위와 같은 내용입니다.",
    createdAt: "2026-08-31T01:02:04Z",
  },
];

/**
 * 턴별 근거 문서 수. 질문 메시지 ID를 키로 씁니다.
 * 메시지 조회 응답에는 없는 정보라, 어느 API로 올지 정해지면 교체합니다.
 */
export const mockSourceCounts: Record<
  number,
  { totalCount: number; foundCount: number }
> = {
  101: { totalCount: 112, foundCount: 3 },
  103: { totalCount: 112, foundCount: 0 }, // 근거 문서가 없는 단순 응답
  105: { totalCount: 112, foundCount: 5 },
};
