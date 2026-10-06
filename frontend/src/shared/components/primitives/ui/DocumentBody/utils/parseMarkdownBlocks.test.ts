import { describe, expect, it } from "vitest";

import { parseMarkdownBlocks } from "./parseMarkdownBlocks";

// 여러 줄 본문을 줄 배열로 적어 읽기 쉽게 해요
const lines = (...rows: string[]) => rows.join("\n");

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
    const paragraphOf = (text: string) => [
      {
        type: "paragraph",
        lines: [[{ type: "text", value: text }]],
        isMuted: false,
      },
    ];

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
});
