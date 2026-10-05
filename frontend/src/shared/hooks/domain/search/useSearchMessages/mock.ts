// 하네스 예외: mock은 원래 shared/api/mock의 msw로 두지만, v2 API 확정 전이라 여기 둠. API 연결 시 이동
import type { SearchMessage } from "@/shared/types/search";

/**
 * 메시지 조회 API를 연결하기 전까지 탐색 대화에 그리는 예시 대화.
 *
 * 근거가 2개인 답변, 3개인 답변, 근거가 없는 답변을 한 턴씩 담아요.
 * 근거는 서버 계약대로 `rank` 오름차순(관련도 높은 순)이에요.
 */
export const SEARCH_MESSAGES_MOCK: SearchMessage[] = [
  {
    id: 1,
    role: "USER",
    sequence: 1,
    content: "탈퇴한 사용자 게시글은 어떻게 처리해?",
    status: "RECEIVED",
    createdAt: "2026-10-05T01:00:00Z",
    evidences: [],
  },
  {
    id: 2,
    role: "ASSISTANT",
    sequence: 2,
    content:
      "댓글이 달린 게시글은 유지하고, 작성자를 ‘탈퇴한 사용자’로 표시해요. 2026년 9월 15일 회의에서 정리한 내용이에요.\n첨부파일 처리는 아직 정해지지 않았어요.",
    status: "COMPLETED",
    createdAt: "2026-10-05T01:00:05Z",
    evidences: [
      {
        documentId: 101,
        title: "회원 탈퇴 정책",
        topic: "회원 관리",
        sourceType: "문서",
        createdAt: "2026-09-15T01:00:00Z",
        rank: 1,
      },
      {
        documentId: 102,
        title: "회원 탈퇴 정책 논의",
        topic: "회의록",
        sourceType: "문서",
        createdAt: "2026-09-15T05:00:00Z",
        rank: 2,
      },
    ],
  },
  {
    id: 3,
    role: "USER",
    sequence: 3,
    content: "알림 설정은 어디까지 정했어?",
    status: "RECEIVED",
    createdAt: "2026-10-05T01:01:00Z",
    evidences: [],
  },
  {
    id: 4,
    role: "ASSISTANT",
    sequence: 4,
    content:
      "푸시 알림은 댓글과 멘션에만 보내기로 했어요.\n방해 금지 시간은 밤 10시부터 아침 8시까지로 정했고, 사용자가 바꿀 수 있어요.",
    status: "COMPLETED",
    createdAt: "2026-10-05T01:01:05Z",
    evidences: [
      {
        documentId: 201,
        title: "푸시 알림 정책",
        topic: "알림",
        sourceType: "문서",
        createdAt: "2026-09-22T01:00:00Z",
        rank: 1,
      },
      {
        documentId: 202,
        title: "알림 설정 화면 기획",
        topic: "기획",
        sourceType: "문서",
        createdAt: "2026-09-24T01:00:00Z",
        rank: 2,
      },
      {
        documentId: 203,
        title: "9월 4주차 정기 회의",
        topic: "회의록",
        sourceType: "문서",
        createdAt: "2026-09-25T05:00:00Z",
        rank: 3,
      },
    ],
  },
  {
    id: 5,
    role: "USER",
    sequence: 5,
    content: "다음 스프린트 회고 일정 정해졌어?",
    status: "RECEIVED",
    createdAt: "2026-10-05T01:02:00Z",
    evidences: [],
  },
  {
    id: 6,
    role: "ASSISTANT",
    sequence: 6,
    content:
      "모아 둔 기록에서 다음 스프린트 회고 일정을 찾지 못했어요.\n일정이 정해지면 회의록에 남겨 주세요.",
    status: "COMPLETED",
    createdAt: "2026-10-05T01:02:05Z",
    evidences: [],
  },
];
