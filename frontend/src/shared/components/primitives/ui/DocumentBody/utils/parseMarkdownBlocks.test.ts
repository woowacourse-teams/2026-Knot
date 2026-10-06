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

  // 정한 문법(# · ## · ### · - · **)만 처리하고 나머지는 바꾸지 않아요 (DOC-R6, 09-29 본문 형식)
  it.each([
    ["번호 목록", "1. 하나"],
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
});
