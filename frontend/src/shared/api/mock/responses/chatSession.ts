import type {
  GetChatSessionsApiResponse,
  PostChatSessionApiResponse,
} from "@api/fetch/api/v1/workspaces/[workspaceId]/conversations";

const SECOND = 1000;
const HOUR = 60 * 60 * SECOND;

// 지난 시각을 고정값으로 두면 언젠가 전부 "이전"으로 묶이므로 지금을 기준으로 생성해요
const fromNow = (elapsed: number) =>
  new Date(Date.now() - elapsed).toISOString();

export const chatSessionsResponse = [
  {
    id: 100,
    title: "DB 기술 선정 관련 문서",
    createdAt: fromNow(2 * HOUR),
    lastMessageAt: fromNow(30 * SECOND),
  },
  {
    id: 101,
    title: "온보딩 문서 위치",
    createdAt: fromNow(26 * HOUR),
    lastMessageAt: fromNow(25 * HOUR),
  },
] satisfies GetChatSessionsApiResponse;

export const chatSessionResponse = {
  id: 102,
  title: "새 대화",
  createdAt: fromNow(0),
  lastMessageAt: fromNow(0),
} satisfies PostChatSessionApiResponse;
