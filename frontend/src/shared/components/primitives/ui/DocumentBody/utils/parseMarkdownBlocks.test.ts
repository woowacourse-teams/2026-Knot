import { describe, expect, it } from "vitest";

import { parseMarkdownBlocks } from "./parseMarkdownBlocks";

// 여러 줄 본문을 줄 배열로 적어 읽기 쉽게 해요
const lines = (...rows: string[]) => rows.join("\n");

// 한 줄이 글자 그대로인 문단 하나가 된 결과
const paragraphOf = (text: string) => [
  {
    type: "paragraph",
    lines: [[{ type: "text", value: text }]],
    isMuted: false,
  },
];

describe("parseMarkdownBlocks", () => {
  it("빈 글이나 앞뒤 빈 줄만 있으면 블록이 없다", () => {
    expect(parseMarkdownBlocks({ content: "" })).toEqual([]);
    expect(parseMarkdownBlocks({ content: lines("", "  ", "") })).toEqual([]);
  });

  it("## 로 시작하는 줄은 level 2 제목이다", () => {
    expect(parseMarkdownBlocks({ content: "## 결정" })).toEqual([
      { type: "heading", level: 2, inlines: [{ type: "text", value: "결정" }] },
    ]);
  });

  it("# 는 level 1, ### 는 level 3 제목이다", () => {
    expect(
      parseMarkdownBlocks({ content: lines("# 제목", "### 소제목") }),
    ).toEqual([
      { type: "heading", level: 1, inlines: [{ type: "text", value: "제목" }] },
      {
        type: "heading",
        level: 3,
        inlines: [{ type: "text", value: "소제목" }],
      },
    ]);
  });

  it("# 가 넷 이상이거나 # 뒤에 띄어쓰기가 없으면 제목이 아니라 글자 그대로인 문단이다", () => {
    expect(parseMarkdownBlocks({ content: "#### 네 개" })).toEqual(
      paragraphOf("#### 네 개"),
    );
    expect(parseMarkdownBlocks({ content: "##결정" })).toEqual(
      paragraphOf("##결정"),
    );
  });

  it("일반 문장 한 줄은 문단 하나다", () => {
    expect(
      parseMarkdownBlocks({ content: "댓글이 달린 글은 유지해요." }),
    ).toEqual([
      {
        type: "paragraph",
        lines: [[{ type: "text", value: "댓글이 달린 글은 유지해요." }]],
        isMuted: false,
      },
    ]);
  });

  it("빈 줄 없이 이어진 줄은 한 문단 안에서 줄바꿈을 유지한다", () => {
    expect(parseMarkdownBlocks({ content: lines("첫 줄", "둘째 줄") })).toEqual(
      [
        {
          type: "paragraph",
          lines: [
            [{ type: "text", value: "첫 줄" }],
            [{ type: "text", value: "둘째 줄" }],
          ],
          isMuted: false,
        },
      ],
    );
  });

  it("빈 줄로 나뉜 글은 서로 다른 문단이다", () => {
    expect(
      parseMarkdownBlocks({ content: lines("문단 1", "", "문단 2") }),
    ).toEqual([
      {
        type: "paragraph",
        lines: [[{ type: "text", value: "문단 1" }]],
        isMuted: false,
      },
      {
        type: "paragraph",
        lines: [[{ type: "text", value: "문단 2" }]],
        isMuted: false,
      },
    ]);
  });

  it("- 로 시작하는 줄이 이어지면 점 목록 하나다", () => {
    expect(parseMarkdownBlocks({ content: lines("- 가", "- 나") })).toEqual([
      {
        type: "list",
        items: [
          [{ type: "text", value: "가" }],
          [{ type: "text", value: "나" }],
        ],
      },
    ]);
  });

  // 하위 목록은 정한 문법이 아니지만, 글자로 두기보다 같은 단계 항목으로 보여 주기로 했어요 (10-06)
  it("앞에 들여쓰기가 있는 - 줄도 하위 목록이 아니라 같은 목록의 항목이다", () => {
    expect(parseMarkdownBlocks({ content: lines("- 가", "  - 나") })).toEqual([
      {
        type: "list",
        items: [
          [{ type: "text", value: "가" }],
          [{ type: "text", value: "나" }],
        ],
      },
    ]);
  });

  it("목록 바로 다음 줄이 - 로 시작하지 않으면 목록이 끝나고 문단이 시작된다", () => {
    expect(parseMarkdownBlocks({ content: lines("- 가", "문장") })).toEqual([
      { type: "list", items: [[{ type: "text", value: "가" }]] },
      {
        type: "paragraph",
        lines: [[{ type: "text", value: "문장" }]],
        isMuted: false,
      },
    ]);
  });

  it("Windows 줄바꿈(\\r\\n)도 \\n과 같은 결과를 낸다", () => {
    expect(parseMarkdownBlocks({ content: "## 결정\r\n문장" })).toEqual(
      parseMarkdownBlocks({ content: "## 결정\n문장" }),
    );
  });

  it("** 로 감싼 글자는 굵게 조각이 되고 나머지는 글자 조각으로 남는다", () => {
    expect(parseMarkdownBlocks({ content: "**중요**한 결정" })).toEqual([
      {
        type: "paragraph",
        lines: [
          [
            { type: "bold", value: "중요" },
            { type: "text", value: "한 결정" },
          ],
        ],
        isMuted: false,
      },
    ]);
  });

  it("한 줄의 ** 쌍은 가까운 짝끼리 각각 굵게 조각이 되고, 앞 · 사이 · 뒤 글자는 글자 조각으로 남는다", () => {
    expect(parseMarkdownBlocks({ content: "앞 **가** 사이 **나** 뒤" })).toEqual([
      {
        type: "paragraph",
        lines: [
          [
            { type: "text", value: "앞 " },
            { type: "bold", value: "가" },
            { type: "text", value: " 사이 " },
            { type: "bold", value: "나" },
            { type: "text", value: " 뒤" },
          ],
        ],
        isMuted: false,
      },
    ]);
  });

  it("짝이 맞지 않는 ** 는 굵게가 아니라 글자 그대로 남는다", () => {
    expect(parseMarkdownBlocks({ content: "**닫히지 않음" })).toEqual([
      {
        type: "paragraph",
        lines: [[{ type: "text", value: "**닫히지 않음" }]],
        isMuted: false,
      },
    ]);
  });

  it("목록 항목과 제목 안의 ** 도 굵게 조각이 된다", () => {
    expect(
      parseMarkdownBlocks({
        content: lines("## **굵은** 제목", "- **첨부파일** 유지 여부"),
      }),
    ).toEqual([
      {
        type: "heading",
        level: 2,
        inlines: [
          { type: "bold", value: "굵은" },
          { type: "text", value: " 제목" },
        ],
      },
      {
        type: "list",
        items: [
          [
            { type: "bold", value: "첨부파일" },
            { type: "text", value: " 유지 여부" },
          ],
        ],
      },
    ]);
  });

  it("숫자와 점으로 시작하는 줄이 이어지면 번호 목록 하나가 된다", () => {
    expect(parseMarkdownBlocks({ content: lines("1. 하나", "2. 둘") })).toEqual(
      [
        {
          type: "orderedList",
          start: 1,
          items: [
            [{ type: "text", value: "하나" }],
            [{ type: "text", value: "둘" }],
          ],
        },
      ],
    );
  });

  it("번호 목록은 첫 항목의 숫자에서 시작하고, 뒤 항목에 적힌 숫자는 쓰지 않는다", () => {
    expect(
      parseMarkdownBlocks({ content: lines("3. 셋", "3. 넷", "9. 다섯") }),
    ).toEqual([
      {
        type: "orderedList",
        start: 3,
        items: [
          [{ type: "text", value: "셋" }],
          [{ type: "text", value: "넷" }],
          [{ type: "text", value: "다섯" }],
        ],
      },
    ]);
  });

  it("들여쓴 번호 줄도 같은 번호 목록의 항목이다", () => {
    expect(
      parseMarkdownBlocks({ content: lines("1. 하나", "   1. 둘") }),
    ).toEqual([
      {
        type: "orderedList",
        start: 1,
        items: [
          [{ type: "text", value: "하나" }],
          [{ type: "text", value: "둘" }],
        ],
      },
    ]);
  });

  it("점 목록과 번호 목록이 이어지면 서로 다른 블록이 된다", () => {
    expect(
      parseMarkdownBlocks({ content: lines("- 가", "1. 나", "- 다") }),
    ).toEqual([
      { type: "list", items: [[{ type: "text", value: "가" }]] },
      {
        type: "orderedList",
        start: 1,
        items: [[{ type: "text", value: "나" }]],
      },
      { type: "list", items: [[{ type: "text", value: "다" }]] },
    ]);
  });

  it("번호 목록은 앞 문단을 끝내고, 뒤따르는 글은 새 문단이 된다", () => {
    expect(
      parseMarkdownBlocks({ content: lines("문장 A", "1. 하나", "문장 B") }),
    ).toEqual([
      ...paragraphOf("문장 A"),
      {
        type: "orderedList",
        start: 1,
        items: [[{ type: "text", value: "하나" }]],
      },
      ...paragraphOf("문장 B"),
    ]);
  });

  it("번호 목록 항목 안의 ** 도 굵게 조각이 된다", () => {
    expect(parseMarkdownBlocks({ content: "1. **법무** 검토" })).toEqual([
      {
        type: "orderedList",
        start: 1,
        items: [
          [
            { type: "bold", value: "법무" },
            { type: "text", value: " 검토" },
          ],
        ],
      },
    ]);
  });

  // 정한 문법(# · ## · ### · - · 1. · **)만 처리하고 나머지는 바꾸지 않아요 (DOC-R6, 09-29 본문 형식)
  it.each([
    ["점 뒤에 공백이 없는 번호", "1.하나"],
    ["괄호 번호", "1) 하나"],
    ["글자 번호", "a. 하나"],
    ["인용", "> 인용"],
    ["코드", "`코드`"],
    ["링크", "[링크](https://example.com)"],
  ])("정한 문법 밖의 %s 는 글자 그대로인 문단이다", (_, text) => {
    expect(parseMarkdownBlocks({ content: text })).toEqual(paragraphOf(text));
  });

  it("HTML 태그도 해석하지 않고 글자 그대로인 문단으로 둔다", () => {
    const html = "<img src=x onerror=alert(1)>";

    expect(parseMarkdownBlocks({ content: html })).toEqual(paragraphOf(html));
  });

  it("문단 전체가 mutedLines의 문장과 같으면 흐리게 그릴 문단이다", () => {
    expect(
      parseMarkdownBlocks({ content: "문장 A", mutedLines: ["문장 A"] }),
    ).toEqual([
      {
        type: "paragraph",
        lines: [[{ type: "text", value: "문장 A" }]],
        isMuted: true,
      },
    ]);
  });

  it("문장 일부만 같거나 mutedLines가 없으면 흐리게 그리지 않는다", () => {
    expect(
      parseMarkdownBlocks({
        content: "문장 A 뒤에 더",
        mutedLines: ["문장 A"],
      }),
    ).toEqual(paragraphOf("문장 A 뒤에 더"));
    expect(parseMarkdownBlocks({ content: "문장 A" })).toEqual(
      paragraphOf("문장 A"),
    );
  });

  // 아래 두 예시는 문서 생성기 형식(09-29)의 예시이자 mock 응답(documentDetailsResponse)과 같은 글이에요
  it("결정이 있는 문서는 제목 4 · 문단 3 · 목록 1, 모두 8개 블록이다", () => {
    const content = lines(
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

    const blocks = parseMarkdownBlocks({ content });

    expect(blocks.map(({ type }) => type)).toEqual([
      "heading",
      "paragraph",
      "heading",
      "paragraph",
      "heading",
      "paragraph",
      "heading",
      "list",
    ]);
    expect(blocks[7]).toEqual({
      type: "list",
      items: [
        [
          {
            type: "text",
            value: "첨부파일을 게시글과 함께 유지할지 — 아직 정해지지 않음",
          },
        ],
        [{ type: "text", value: "탈퇴 후 복구 기간 — 다음 논의에서 확인" }],
      ],
    });
  });

  it("결정이 없는 문서는 제목 3 · 문단 1 · 목록 2이고, 결정 없음 문장만 흐리게 그린다", () => {
    const noDecisionSentence = "이번 회의에서 정해진 내용은 없어요.";
    const content = lines(
      "## 결정",
      noDecisionSentence,
      "",
      "## 논의한 내용",
      "- 개편 범위 — 홈 전체 개편과 일부 개선, 두 안을 비교했어요",
      "- 출시 시점 — 다음 분기 안에 가능한지 이야기했어요",
      "",
      "## 미결정 항목",
      "- 개편 범위 — 디자인 시안을 보고 정하기로 함",
      "- 출시 일정 — 개발 공수를 확인한 뒤 정하기로 함",
    );

    const blocks = parseMarkdownBlocks({
      content,
      mutedLines: [noDecisionSentence],
    });

    expect(blocks.map(({ type }) => type)).toEqual([
      "heading",
      "paragraph",
      "heading",
      "list",
      "heading",
      "list",
    ]);
    expect(blocks[1]).toEqual({
      type: "paragraph",
      lines: [[{ type: "text", value: noDecisionSentence }]],
      isMuted: true,
    });
  });
});
