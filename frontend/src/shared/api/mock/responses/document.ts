import type { DocumentDetailResponse } from "@api/mock/types/document";

const DAY = 24 * 60 * 60 * 1000;

// 지난 시각을 고정값으로 두면 언젠가 전부 오래된 날짜가 되므로 지금을 기준으로 만들어요
const fromNow = (elapsed: number) =>
  new Date(Date.now() - elapsed).toISOString();

// 같은 녹음(42)에서 나온 두 문서예요. 본문은 문서 생성기 형식(09-29)의 두 예시(결정 있음 · 결정 없음)를 그대로 써요.
// id만 탐색 mock의 근거 문서 id(101 · 102)와 맞춰, 찾은 기록 카드를 누르면 404 대신 문서가 열려요.
// 101은 제목 · 주제도 카드와 같아요. 102는 결정 없음 예시를 써야 해서 맞추지 않았어요.
// 그래서 「회원 탈퇴 정책 논의」 카드를 누르면 「홈 개편 논의」 문서가 열려요.
export const documentDetailsResponse = [
  {
    id: 101,
    recordingSessionId: 42,
    topic: "회원 관리",
    title: "회원 탈퇴 정책",
    summary: "탈퇴한 사용자의 게시글을 남기는 기준을 정했어요.",
    content: [
      "## 결정",
      "탈퇴한 사용자의 게시글은 유지하고, 작성자를 '탈퇴한 사용자'로 표시해요.",
      "",
      "## 적용 범위",
      "댓글이 달린 게시글만 유지하고, 댓글 없는 글은 함께 삭제해요.",
      "",
      "## 이유",
      "댓글이 달린 글이 사라지면 대화 흐름이 끊겨요.",
      "",
      "## 미결정 항목",
      "- 첨부파일을 게시글과 함께 유지할지 — 아직 정해지지 않음",
      "- 탈퇴 후 복구 기간 — 다음 논의에서 확인",
    ].join("\n"),
    status: "DRAFT",
    createdAt: fromNow(2 * DAY),
    archivedAt: null,
    recordingDurationSeconds: 1920,
    sourceTranscriptId: 81,
    myConfirmationState: "PENDING",
    confirmationSummary: {
      confirmedCount: 2,
      pendingCount: 2,
      excludedCount: 0,
    },
  },
  {
    id: 102,
    recordingSessionId: 42,
    topic: "주간 회의",
    title: "홈 개편 논의",
    summary: null,
    content: [
      "## 결정",
      "이번 회의에서 정해진 내용은 없어요.",
      "",
      "## 논의한 내용",
      "- 개편 범위 — 홈 전체 개편과 일부 개선, 두 안을 비교했어요",
      "- 출시 시점 — 다음 분기 안에 가능한지 이야기했어요",
      "",
      "## 미결정 항목",
      "- 개편 범위 — 디자인 시안을 보고 정하기로 함",
      "- 출시 일정 — 개발 공수를 확인한 뒤 정하기로 함",
    ].join("\n"),
    status: "DRAFT",
    createdAt: fromNow(2 * DAY),
    archivedAt: null,
    recordingDurationSeconds: 1920,
    sourceTranscriptId: 81,
    myConfirmationState: "CONFIRMED",
    confirmationSummary: {
      confirmedCount: 3,
      pendingCount: 1,
      excludedCount: 0,
    },
  },
] satisfies DocumentDetailResponse[];
