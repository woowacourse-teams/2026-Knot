import { describe, expect, it } from "vitest";

import type { MarkdownBlock } from "../types/markdownBlock";
import { groupBlocksBySection } from "./groupBlocksBySection";

const heading = (value: string) =>
  ({
    type: "heading",
    level: 2,
    inlines: [{ type: "text", value }],
  }) satisfies MarkdownBlock;

const paragraph = (value: string) =>
  ({
    type: "paragraph",
    lines: [[{ type: "text", value }]],
    isMuted: false,
  }) satisfies MarkdownBlock;

describe("groupBlocksBySection", () => {
  it("블록이 없으면 구역도 없다", () => {
    expect(groupBlocksBySection([])).toEqual([]);
  });

  it("제목이 없으면 모든 블록이 구역 하나에 들어간다", () => {
    expect(
      groupBlocksBySection([paragraph("문단 1"), paragraph("문단 2")]),
    ).toEqual([[paragraph("문단 1"), paragraph("문단 2")]]);
  });

  it("제목 블록마다 새 구역을 시작하고, 뒤따르는 블록은 그 구역에 들어간다", () => {
    expect(
      groupBlocksBySection([
        heading("결정"),
        paragraph("문장 A"),
        heading("이유"),
        paragraph("문장 B"),
      ]),
    ).toEqual([
      [heading("결정"), paragraph("문장 A")],
      [heading("이유"), paragraph("문장 B")],
    ]);
  });

  it("첫 제목 앞의 블록은 제목 없는 구역 하나가 된다", () => {
    expect(
      groupBlocksBySection([
        paragraph("머리말"),
        heading("결정"),
        paragraph("문장 A"),
      ]),
    ).toEqual([[paragraph("머리말")], [heading("결정"), paragraph("문장 A")]]);
  });
});
