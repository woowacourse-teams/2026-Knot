import { describe, it, expect } from "vitest";

import type { ChatMessage } from "../types/chatMessage";
import { toChatTurns } from "./toChatTurns";

const message = (
  id: number,
  role: ChatMessage["role"],
  content: string,
): ChatMessage => ({
  id,
  role,
  content,
  createdAt: `2026-08-31T01:00:${String(id).padStart(2, "0")}Z`,
});

describe("toChatTurns", () => {
  it("메시지가 없으면 빈 배열을 돌려준다", () => {
    expect(toChatTurns([])).toEqual([]);
  });

  it("질문과 답변 한 쌍을 완료된 턴 하나로 묶는다", () => {
    const turns = toChatTurns([
      message(1, "USER", "DB 기술 선정 문서 있어?"),
      message(2, "ASSISTANT", "PostgreSQL로 정해졌어요."),
    ]);

    expect(turns).toEqual([
      {
        id: 1,
        question: "DB 기술 선정 문서 있어?",
        answer: "PostgreSQL로 정해졌어요.",
        status: "done",
      },
    ]);
  });

  it("여러 쌍을 받은 순서대로 턴으로 묶는다", () => {
    const turns = toChatTurns([
      message(1, "USER", "첫 질문"),
      message(2, "ASSISTANT", "첫 답변"),
      message(3, "USER", "둘째 질문"),
      message(4, "ASSISTANT", "둘째 답변"),
    ]);

    expect(turns.map(({ question, answer }) => [question, answer])).toEqual([
      ["첫 질문", "첫 답변"],
      ["둘째 질문", "둘째 답변"],
    ]);
  });

  it("답변이 아직 없는 질문은 pending 턴이 된다", () => {
    const turns = toChatTurns([
      message(1, "USER", "첫 질문"),
      message(2, "ASSISTANT", "첫 답변"),
      message(3, "USER", "방금 보낸 질문"),
    ]);

    expect(turns[turns.length - 1]).toEqual({
      id: 3,
      question: "방금 보낸 질문",
      answer: null,
      status: "pending",
    });
  });

  it("답변 없이 질문이 연달아 있으면 각각 별도의 pending 턴이 된다", () => {
    const turns = toChatTurns([
      message(1, "USER", "답변을 못 받은 질문"),
      message(2, "USER", "다시 보낸 질문"),
      message(3, "ASSISTANT", "답변"),
    ]);

    expect(turns).toHaveLength(2);
    expect(turns[0].status).toBe("pending");
    expect(turns[1].status).toBe("done");
  });

  it("답변이 빈 문자열이어도 완료된 턴으로 본다", () => {
    const turns = toChatTurns([
      message(1, "USER", "질문"),
      message(2, "ASSISTANT", ""),
    ]);

    expect(turns[0]).toMatchObject({ answer: "", status: "done" });
  });

  it("짝이 없는 답변은 턴을 만들지 않고 건너뛴다", () => {
    const turns = toChatTurns([
      message(1, "ASSISTANT", "질문보다 먼저 온 답변"),
      message(2, "USER", "질문"),
      message(3, "ASSISTANT", "답변"),
    ]);

    expect(turns).toEqual([
      { id: 2, question: "질문", answer: "답변", status: "done" },
    ]);
  });

  it("한 턴에 답변이 두 번 오면 첫 답변만 쓴다", () => {
    const turns = toChatTurns([
      message(1, "USER", "질문"),
      message(2, "ASSISTANT", "첫 답변"),
      message(3, "ASSISTANT", "덧붙인 답변"),
    ]);

    expect(turns).toEqual([
      { id: 1, question: "질문", answer: "첫 답변", status: "done" },
    ]);
  });
});
