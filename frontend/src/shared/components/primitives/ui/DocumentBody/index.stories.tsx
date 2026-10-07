import type { Meta, StoryObj } from "@storybook/react-webpack5";

import DocumentBody from ".";

// 여러 줄 본문을 줄 배열로 적어 읽기 쉽게 해요
const lines = (...rows: string[]) => rows.join("\n");

const DECISION_CONTENT = lines(
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
);

const NO_DECISION_SENTENCE = "이번 회의에서 정해진 내용은 없어요.";

const NO_DECISION_CONTENT = lines(
  "## 결정",
  NO_DECISION_SENTENCE,
  "",
  "## 논의한 내용",
  "- 개편 범위 — 홈 전체 개편과 일부 개선, 두 안을 비교했어요",
  "- 출시 시점 — 다음 분기 안에 가능한지 이야기했어요",
  "",
  "## 미결정 항목",
  "- 개편 범위 — 디자인 시안을 보고 정하기로 함",
  "- 출시 일정 — 개발 공수를 확인한 뒤 정하기로 함",
);

const ALL_SYNTAX_CONTENT = lines(
  "# 회원 탈퇴 정책 회의",
  "## 결정",
  "탈퇴한 사용자의 게시글은 **유지**하고, 작성자를 '탈퇴한 사용자'로 표시해요.",
  "댓글 없는 글은 함께 삭제해요.",
  "",
  "### 예외",
  "- **공지 글**은 삭제하지 않아요",
  "  - 들여쓴 줄도 같은 목록의 항목으로 보여요",
  "",
  "## 다음 할 일",
  "1. 탈퇴 안내 문구 정리하기",
  "2. **법무** 검토 요청하기",
  "3. 복구 기간 정하기",
  "",
  "## 정한 문법 밖의 글",
  "1) 괄호 번호는 글자 그대로 보여요",
  "[링크](https://example.com)도 글자 그대로 보여요",
  "<b>HTML 태그</b>도 실행되지 않고 글자로 보여요",
);

/**
 * 문서 본문(Markdown)을 읽기 전용으로 그리는 본문이에요.
 * 문서 보기 화면과 녹음 직후 문서 확인 화면에서 제목 아래에 놓여요.
 *
 * **그리는 규칙**
 * - `##`는 구역 제목(결정 · 적용 범위 · 이유 · 미결정 항목)이에요. `#`와 `###`도 한 단계 크거나 작은 제목으로 그려요.
 * - 빈 줄로 나뉜 글은 문단, `- `로 시작하는 줄은 점 목록, `**글자**`는 굵게 그려요.
 * - `1. `처럼 숫자와 점으로 시작하는 줄은 번호 목록으로 그려요. 첫 줄의 숫자에서 시작해 1씩 늘려요.
 * - 들여쓴 목록 줄은 하위 목록이 아니라 같은 목록의 항목으로 그려요.
 * - 그 밖의 문법(링크 · 인용 · HTML)은 바꾸지 않고 글자 그대로 보여 줘요. 본문에 HTML이 섞여 와도 실행되지 않아요.
 * - 구역 사이는 24px, 구역 제목과 그 아래 내용 사이는 8px이에요.
 *
 * **흐리게**
 * - `mutedLines`에 넘긴 문장과 문단 전체가 같으면 그 문단만 흐리게 그려요.
 * - 어떤 문장을 흐리게 할지는 이 컴포넌트가 아니라 쓰는 화면이 정해요. 문서 보기 화면은 결정 없음 문장을 넘겨요.
 */
const meta = {
  title: "Shared/DocumentBody",
  component: DocumentBody,
  parameters: {
    design: [
      {
        name: "문서/확인한 사람 보기",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36058",
      },
      {
        name: "문서/녹음 직후 · 결정 없음",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2259-32252",
      },
      {
        name: "문서/마크다운 요소",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2064-25992",
      },
    ],
  },
  decorators: [
    // 실제 화면의 문서 열 폭(720px)에서 줄이 어떻게 나뉘는지 보이게 해요
    (Story) => (
      <div style={{ maxWidth: "45rem" }}>
        <Story />
      </div>
    ),
  ],
  args: { content: DECISION_CONTENT },
} satisfies Meta<typeof DocumentBody>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 결정이 있는 문서의 기본 모양이에요. 구역 제목 네 개와 문단, 미결정 항목 목록으로 이뤄져요. */
export const Default: Story = {};

/** 결정이 없는 회의 문서예요. 결정 자리의 정해진 문장만 흐리게 보이고, 논의한 내용 구역이 생겨요. */
export const NoDecision: Story = {
  args: {
    content: NO_DECISION_CONTENT,
    mutedLines: [NO_DECISION_SENTENCE],
  },
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2259-32252",
    },
  },
};

/**
 * 정한 문법 전부와 그 밖의 글이 어떻게 보이는지 확인할 때 봐요.
 * 문서 생성기는 `##` · 문단 · 목록 · 굵게만 쓰지만, 다른 글이 들어와도 화면이 깨지지 않아요.
 */
export const AllSyntax: Story = {
  args: { content: ALL_SYNTAX_CONTENT },
};
