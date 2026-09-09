import type {
  ChatMessage,
  ChatMessageSourcesResponse,
  ChatMessageStream,
  SearchReferencePage,
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

/** 출처 목록이 공유하는 원본 페이지. 같은 페이지의 청크가 여러 건 오는 모습을 재현하려고 셋만 둬요 */
const decisionMeetingPage = {
  id: "14f2a8c1-7e3b-4d6a-9f21-8c5b0a12d934",
  title: "DB 기술 선정 회의록",
  notionUrl: "https://www.notion.so/db-decision-meeting",
  createdAt: "2026-08-30T00:00:00Z",
  updatedAt: "2026-09-01T04:12:35Z",
} satisfies SearchReferencePage;

const erdPage = {
  id: "2b7c9d10-5a44-4e0f-8b2d-1f3e6a7c8d90",
  title: "초기 스키마 ERD",
  notionUrl: "https://www.notion.so/initial-schema-erd",
  createdAt: "2026-08-31T00:00:00Z",
  updatedAt: "2026-09-02T09:30:00Z",
} satisfies SearchReferencePage;

const roadmapPage = {
  id: "9e1f0a23-6b7c-4d8e-9f01-2a3b4c5d6e7f",
  title: "2026 H2 제품 로드맵",
  notionUrl: "https://www.notion.so/2026-h2-roadmap",
  createdAt: "2026-08-20T00:00:00Z",
  updatedAt: "2026-08-28T12:00:00Z",
} satisfies SearchReferencePage;

/**
 * 답변 출처 조회 응답. 청크 단위 8건이 관련도 순위(rank) 순으로 오고, 같은 페이지가 여러 번 섞여 있어요.
 *
 * 화면은 이 8건을 페이지로 묶어 페이지의 대표 점수(가장 높은 청크 점수) 순으로 보여 줍니다.
 */
export const chatMessageSourcesResponse = {
  searchReferences: [
    {
      id: 1,
      messageId: 1002,
      rank: 1,
      relevanceScore: 0.95,
      source: "NOTION",
      notionPage: decisionMeetingPage,
    },
    {
      id: 2,
      messageId: 1002,
      rank: 2,
      relevanceScore: 0.91,
      source: "NOTION",
      notionPage: erdPage,
    },
    {
      id: 3,
      messageId: 1002,
      rank: 3,
      relevanceScore: 0.88,
      source: "NOTION",
      notionPage: decisionMeetingPage,
    },
    {
      id: 4,
      messageId: 1002,
      rank: 4,
      relevanceScore: 0.8,
      source: "NOTION",
      notionPage: roadmapPage,
    },
    {
      id: 5,
      messageId: 1002,
      rank: 5,
      relevanceScore: 0.74,
      source: "NOTION",
      notionPage: erdPage,
    },
    {
      id: 6,
      messageId: 1002,
      rank: 6,
      relevanceScore: 0.66,
      source: "NOTION",
      notionPage: decisionMeetingPage,
    },
    {
      id: 7,
      messageId: 1002,
      rank: 7,
      relevanceScore: 0.55,
      source: "NOTION",
      notionPage: roadmapPage,
    },
    {
      id: 8,
      messageId: 1002,
      rank: 8,
      relevanceScore: 0.41,
      source: "NOTION",
      notionPage: erdPage,
    },
  ],
} satisfies ChatMessageSourcesResponse;
