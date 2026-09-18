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

/**
 * 답변이 조각으로 도착하는 모습을 화면에서 확인하려고 일부러 잘게 나눠 둔 응답이에요.
 *
 * 실제 Fake LLM은 `"테스트 "`, `"LLM 응답입니다."` 두 조각만 돌려줍니다. 두 조각은 순식간에
 * 끝나서 스트리밍인지 한 덩어리 응답인지 눈으로 구분할 수 없어, 개발 중 확인을 위해
 * 조각 수를 늘렸습니다. 백엔드의 실제 LLM이 붙으면 그때의 조각 모양으로 맞춥니다.
 */
export const chatMessageStreamResponse = {
  deltas: [
    "팀 문서에서 ",
    "관련 내용을 ",
    "찾아봤어요.\n\n",
    "DB는 ",
    "지난주 ",
    "기술 선정 회의에서 ",
    "PostgreSQL",
    "로 정해졌고, ",
    "초기 스키마는 ",
    "제품/스펙 아래 ",
    "ERD 문서에 ",
    "정리돼 있어요. ",
    "합의한 범위 밖의 ",
    "테이블은 ",
    "이번 스프린트에서 ",
    "잡지 않기로 ",
    "했습니다.\n\n",
    "스키마를 ",
    "넓혀야 한다면 ",
    "먼저 회의록에 ",
    "안건으로 ",
    "올려 주세요.",
  ],
  messageId: 102,
} satisfies ChatMessageStream;
