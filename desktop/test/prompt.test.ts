import { describe, expect, it } from "vitest";
import { buildSystemPrompt, toLlmMessages } from "../src/main/chat/prompt";

const RULES = "다음 규칙을 반드시 지켜 답변하세요.\n- 근거만.\n\n";

const chunk = (index: number) => ({
  importRunId: 300 + index,
  importedPageId: 200 + index,
  chunkIndex: index,
  title: `문서 ${index}`,
  sourceUrl: `https://www.notion.so/page-${index}`,
  content: `본문 ${index}`,
  score: 0.9 - index / 10,
});

describe("buildSystemPrompt", () => {
  // 백엔드 SearchContext.groundingBlock과 글자 단위로 같아야 SSE·IPC 두 경로의 프롬프트가 일치한다
  it("규칙 문장 뒤에 [근거 문서 n] 블록을 서버와 같은 형식으로 잇는다", () => {
    const prompt = buildSystemPrompt(RULES, [chunk(0), chunk(1)]);

    expect(prompt).toBe(
      RULES +
        "[근거 문서 1]\n제목: 문서 0\n문서 ID: 200\n문서 링크: https://www.notion.so/page-0\n내용:\n본문 0\n\n" +
        "[근거 문서 2]\n제목: 문서 1\n문서 ID: 201\n문서 링크: https://www.notion.so/page-1\n내용:\n본문 1\n\n",
    );
  });

  it("청크가 없으면 규칙 문장만 남는다", () => {
    expect(buildSystemPrompt(RULES, [])).toBe(RULES);
  });
});

describe("toLlmMessages", () => {
  it("이력을 user/assistant로 옮기고 마지막이 현재 질문이면 그대로 둔다", () => {
    const messages = toLlmMessages(
      [
        { id: 1, role: "USER", content: "첫 질문" },
        { id: 2, role: "ASSISTANT", content: "첫 답" },
        { id: 3, role: "USER", content: "지금 질문" },
      ],
      "지금 질문",
    );

    expect(messages).toEqual([
      { role: "user", content: "첫 질문" },
      { role: "assistant", content: "첫 답" },
      { role: "user", content: "지금 질문" },
    ]);
  });

  // Messages API는 user/assistant가 번갈아 오는 대화를 기대한다(서버 AnthropicRequestMapper와 같은 규칙)
  it("같은 역할이 연속되면 한 turn으로 합친다", () => {
    const messages = toLlmMessages(
      [
        { id: 1, role: "USER", content: "질문 A" },
        { id: 2, role: "USER", content: "질문 B" },
        { id: 3, role: "ASSISTANT", content: "답 1" },
        { id: 4, role: "ASSISTANT", content: "답 2" },
        { id: 5, role: "USER", content: "지금" },
      ],
      "지금",
    );

    expect(messages).toEqual([
      { role: "user", content: "질문 A\n\n질문 B" },
      { role: "assistant", content: "답 1\n\n답 2" },
      { role: "user", content: "지금" },
    ]);
  });

  it("이력이 비었거나 마지막이 현재 질문이 아니면 질문을 덧붙인다", () => {
    expect(toLlmMessages([], "질문")).toEqual([{ role: "user", content: "질문" }]);

    expect(toLlmMessages([{ id: 1, role: "ASSISTANT", content: "안내" }], "질문")).toEqual([
      { role: "assistant", content: "안내" },
      { role: "user", content: "질문" },
    ]);

    expect(toLlmMessages([{ id: 1, role: "USER", content: "다른 질문" }], "질문")).toEqual([
      { role: "user", content: "다른 질문\n\n질문" },
    ]);
  });
});
