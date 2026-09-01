import type {
  ChatMessage,
  ChatMessageStream,
} from "@api/mock/types/chatMessage";

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;

// 고정 시각은 언젠가 전부 "이전"으로 묶이므로 지금 기준으로 만들어요
const fromNow = (elapsed: number) =>
  new Date(Date.now() - elapsed).toISOString();

export const chatMessagesResponse = [
  {
    id: 1001,
    role: "USER",
    content: "DB 기술 선정 관련해서 정리된 문서 있어?",
    createdAt: fromNow(2 * HOUR),
  },
  {
    id: 1002,
    role: "ASSISTANT",
    content:
      "DB는 PostgreSQL로 정해졌어요. 지난주 기술 선정 회의에서 결정됐고, 초기 스키마는 FE와 합의한 범위 안에서만 잡기로 했습니다.",
    createdAt: fromNow(2 * HOUR - 3 * SECOND),
  },
  {
    id: 1003,
    role: "USER",
    content: "그럼 초기 스키마는 어디에 정리돼 있어?",
    createdAt: fromNow(2 * HOUR - 2 * MINUTE),
  },
  {
    id: 1004,
    role: "ASSISTANT",
    content:
      "초기 스키마는 제품/스펙 아래 ERD 문서에 정리돼 있어요. 회의록에서 합의한 범위와 같은 내용입니다.",
    createdAt: fromNow(2 * HOUR - 2 * MINUTE - 3 * SECOND),
  },
] satisfies ChatMessage[];

// 현재 Fake LLM이 돌려주는 두 조각이에요
export const chatMessageStreamResponse = {
  deltas: ["테스트 ", "LLM 응답입니다."],
  messageId: 102,
} satisfies ChatMessageStream;
