import { describe, expect, it } from "vitest";

import type { SearchMessage } from "@/shared/types/search";

import { toSearchTurns } from "./toSearchTurns";

const message = (
  id: number,
  role: SearchMessage["role"],
  content: string,
): SearchMessage => ({
  id,
  role,
  sequence: id,
  content,
  status: role === "USER" ? "RECEIVED" : "COMPLETED",
  createdAt: `2026-10-05T01:00:${String(id).padStart(2, "0")}Z`,
  evidences: [],
});

describe("toSearchTurns", () => {
  it("메시지가 없으면 빈 배열을 돌려준다", () => {
    expect(toSearchTurns([])).toEqual([]);
  });

  it("질문과 답변 한 쌍을 턴 하나로 묶는다", () => {
    const question = message(
      1,
      "USER",
      "탈퇴한 사용자 게시글은 어떻게 처리해?",
    );
    const answer = message(
      2,
      "ASSISTANT",
      "작성자를 ‘탈퇴한 사용자’로 표시해요.",
    );

    expect(toSearchTurns([question, answer])).toEqual([{ question, answer }]);
  });

  it("여러 쌍을 받은 순서대로 턴으로 묶는다", () => {
    const turns = toSearchTurns([
      message(1, "USER", "첫 질문"),
      message(2, "ASSISTANT", "첫 답변"),
      message(3, "USER", "둘째 질문"),
      message(4, "ASSISTANT", "둘째 답변"),
    ]);

    expect(
      turns.map(({ question, answer }) => [question.content, answer?.content]),
    ).toEqual([
      ["첫 질문", "첫 답변"],
      ["둘째 질문", "둘째 답변"],
    ]);
  });

  it("답변이 아직 없는 질문은 답변이 null인 턴이 된다", () => {
    const question = message(3, "USER", "방금 보낸 질문");
    const turns = toSearchTurns([
      message(1, "USER", "첫 질문"),
      message(2, "ASSISTANT", "첫 답변"),
      question,
    ]);

    expect(turns[turns.length - 1]).toEqual({ question, answer: null });
  });

  it("답변 없이 질문이 연달아 있으면 각각 별도의 턴이 된다", () => {
    const turns = toSearchTurns([
      message(1, "USER", "답변을 못 받은 질문"),
      message(2, "USER", "다시 보낸 질문"),
      message(3, "ASSISTANT", "답변"),
    ]);

    expect(turns.map(({ answer }) => answer?.id ?? null)).toEqual([null, 3]);
  });

  it("짝이 없는 답변은 턴을 만들지 않고 건너뛴다", () => {
    const question = message(2, "USER", "질문");
    const answer = message(3, "ASSISTANT", "답변");

    const turns = toSearchTurns([
      message(1, "ASSISTANT", "질문보다 먼저 온 답변"),
      question,
      answer,
    ]);

    expect(turns).toEqual([{ question, answer }]);
  });

  it("한 턴에 답변이 두 번 오면 첫 답변만 쓴다", () => {
    const question = message(1, "USER", "질문");
    const answer = message(2, "ASSISTANT", "첫 답변");

    const turns = toSearchTurns([
      question,
      answer,
      message(3, "ASSISTANT", "덧붙인 답변"),
    ]);

    expect(turns).toEqual([{ question, answer }]);
  });
});
