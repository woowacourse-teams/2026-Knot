import type {
  SearchAnswerStream,
  SearchQuestionAnswerStream,
} from "@api/mock/types/search";

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

/**
 * 첫 질문에 이어 보내는 후속 질문 응답이에요.
 * 같은 대화에 메시지가 쌓이므로 첫 질문 응답과 메시지 ID가 겹치지 않게 둬요.
 */
export const searchQuestionAnswerStreamResponse = {
  questionMessageId: 1003,
  answerMessageId: 1004,
  stages: ["SEARCHING", "GENERATING"],
  deltas: ["초기 스키마는 ", "백엔드 팀이 ", "ERD 문서로 ", "정리했어요."],
  evidences: [
    {
      documentId: 12,
      title: "초기 ERD",
      topic: "데이터 모델",
      rank: 1,
    },
  ],
} satisfies SearchQuestionAnswerStream;
