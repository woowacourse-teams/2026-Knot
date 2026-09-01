import type { ChatSession } from "./types/chatSession";

/** 채팅 세션 목록 조회 응답. 마지막 메시지가 최근인 세션부터 옵니다. */
export type ChatSessionListResponse = ChatSession[];

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

/** 지난 시각을 기준 없이 적어두면 언젠가 전부 "이전"으로 묶이므로 지금을 기준으로 만듭니다. */
const fromNow = (elapsed: number) =>
  new Date(Date.now() - elapsed).toISOString();

/** 백엔드가 붙기 전까지 대화 목록을 그려보기 위한 임시 데이터 */
export const mockChatSessions = [
  {
    id: 100,
    title: "DB 기술 선정 관련 문서",
    createdAt: fromNow(2 * HOUR),
    lastMessageAt: fromNow(30 * 1000),
  },
  {
    id: 99,
    title: "온보딩 개선안 어디까지 됐어?",
    createdAt: fromNow(2 * DAY),
    lastMessageAt: fromNow(2 * DAY),
  },
  {
    id: 98,
    title: "스프린트 회고 요약해줘",
    createdAt: fromNow(3 * DAY),
    lastMessageAt: fromNow(3 * DAY),
  },
  {
    id: 97,
    title: "퍼널 지표 정의 문서 찾기",
    createdAt: fromNow(14 * DAY),
    lastMessageAt: fromNow(14 * DAY),
  },
  {
    id: 96,
    title: "노션 동기화 오류 확인",
    createdAt: fromNow(21 * DAY),
    lastMessageAt: fromNow(21 * DAY),
  },
  {
    id: 95,
    title: "문서 작성 규칙 초안 어디 있어?",
    createdAt: fromNow(28 * DAY),
    lastMessageAt: fromNow(28 * DAY),
  },
] satisfies ChatSessionListResponse;
