import type { SearchAnswerStream } from "@api/mock/types/search";

/**
 * 답변이 조각으로 도착하는 모습을 화면에서 확인하려고 일부러 잘게 나눠 둔 첫 질문 응답이에요.
 * 실제 LLM이 붙으면 그때의 조각 모양으로 맞춰요.
 */
export const searchAnswerStreamResponse = {
  conversationId: 100,
  questionMessageId: 1001,
  answerMessageId: 1002,
  stages: ["SEARCHING", "GENERATING"],
  deltas: [
    "DB는 ",
    "지난주 ",
    "기술 선정 회의에서 ",
    "PostgreSQL",
    "로 정해졌어요. ",
    "초기 스키마는 ",
    "ERD 문서에 ",
    "정리돼 있어요.",
  ],
  evidences: [
    {
      documentId: 11,
      title: "DB 기술 선정 회의록",
      topic: "DB 기술 선정",
      rank: 1,
    },
    {
      documentId: 12,
      title: "초기 ERD",
      topic: "데이터 모델",
      rank: 2,
    },
    {
      documentId: 13,
      title: "백엔드 기술 스택 정리",
      topic: "기술 스택",
      rank: 3,
    },
  ],
} satisfies SearchAnswerStream;
