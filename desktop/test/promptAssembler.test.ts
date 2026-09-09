import { describe, expect, it } from "vitest";
import type { ChatMessageSummary, SearchChunk } from "../src/main/chat/knotApi";
import {
  MAX_HISTORY_CHARACTERS,
  MAX_HISTORY_MESSAGES,
  buildMessages,
  buildSystemPrompt,
} from "../src/main/llm/promptAssembler";

const chunk = (index: number, content = "본문"): SearchChunk => ({
  importRunId: 300 + index,
  importedPageId: 200 + index,
  chunkIndex: index,
  title: `제목${index}`,
  sourceUrl: `https://n.so/${index}`,
  content,
  score: 0.9,
});

const message = (id: number, role: ChatMessageSummary["role"], content: string): ChatMessageSummary => ({
  id,
  role,
  content,
  createdAt: "2026-09-09T00:00:00Z",
});

describe("buildSystemPrompt", () => {
  it("규칙 문장 뒤에 서버 SSE 경로와 같은 [근거 문서 n] 블록을 붙인다(청크 순번 줄 없음)", () => {
    const system = buildSystemPrompt("규칙\n\n", [chunk(0), chunk(1, "둘째")]);

    expect(system).toBe(
      "규칙\n\n" +
        "[근거 문서 1]\n제목: 제목0\n문서 ID: 200\n문서 링크: https://n.so/0\n내용:\n본문\n\n" +
        "[근거 문서 2]\n제목: 제목1\n문서 ID: 201\n문서 링크: https://n.so/1\n내용:\n둘째\n\n",
    );
    expect(system).not.toContain("청크:");
  });

  it("근거가 없으면 규칙 문장만이다", () => {
    expect(buildSystemPrompt("규칙", [])).toBe("규칙");
  });
});

describe("buildMessages", () => {
  it("히스토리가 없으면 질문 하나다", () => {
    expect(buildMessages([], "질문")).toEqual([{ role: "user", content: "질문" }]);
  });

  it("직전 메시지를 user/assistant로 옮기고 질문을 끝에 붙인다", () => {
    const history = [message(1, "USER", "q1"), message(2, "ASSISTANT", "a1")];

    expect(buildMessages(history, "q2")).toEqual([
      { role: "user", content: "q1" },
      { role: "assistant", content: "a1" },
      { role: "user", content: "q2" },
    ]);
  });

  it("마지막 4개만 쓰고, 앞머리의 assistant는 버린다(첫 메시지는 user)", () => {
    const history = [
      message(1, "USER", "q1"),
      message(2, "ASSISTANT", "a1"),
      message(3, "USER", "q2"),
      message(4, "ASSISTANT", "a2"),
      message(5, "USER", "q3"),
      message(6, "ASSISTANT", "a3"),
    ];

    expect(MAX_HISTORY_MESSAGES).toBe(4);
    // 마지막 4개: q2, a2, q3, a3
    expect(buildMessages(history, "q4")).toEqual([
      { role: "user", content: "q2" },
      { role: "assistant", content: "a2" },
      { role: "user", content: "q3" },
      { role: "assistant", content: "a3" },
      { role: "user", content: "q4" },
    ]);
    // 앞머리가 assistant(a1)로 시작하면 버리고, 남은 user(q2)는 이번 질문과 한 turn이 된다
    expect(buildMessages(history.slice(1, 3), "q")).toEqual([{ role: "user", content: "q2\n\nq" }]);
  });

  it("4,000자를 넘으면 오래된 것부터 뺀다", () => {
    // long + a1 + q2 + a2 = 4,001자라 상한을 넘는다
    const long = "가".repeat(MAX_HISTORY_CHARACTERS - 5);
    const history = [message(1, "USER", long), message(2, "ASSISTANT", "a1"), message(3, "USER", "q2"), message(4, "ASSISTANT", "a2")];

    expect(buildMessages(history, "q3")).toEqual([
      // long이 빠지면 a1이 앞머리가 되어 버려진다
      { role: "user", content: "q2" },
      { role: "assistant", content: "a2" },
      { role: "user", content: "q3" },
    ]);
  });

  it("같은 역할이 이어지면 한 turn으로 합치고, 답변 없이 남은 직전 질문은 이번 질문과 합친다", () => {
    const history = [message(1, "USER", "q1"), message(2, "USER", "q1-again"), message(3, "ASSISTANT", "a1"), message(4, "USER", "만료된 질문")];

    expect(buildMessages(history, "q2")).toEqual([
      { role: "user", content: "q1\n\nq1-again" },
      { role: "assistant", content: "a1" },
      { role: "user", content: "만료된 질문\n\nq2" },
    ]);
  });
});
