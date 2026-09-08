/**
 * 프롬프트 조립 (기획서 6.4 "프롬프트", 로드맵 Q43). 순수 함수.
 *
 * system = 서버가 준 규칙 문장 + `[근거 문서 n]` 블록 × ≤8. 블록 형식은 백엔드
 * `SearchContext.groundingBlock`과 글자 단위로 같다 — 브라우저 단독(SSE) 경로와 데스크톱
 * 경로의 프롬프트가 같아야 gold set 비교가 성립한다(`S5`).
 * messages = 세션 이력 전체. 같은 역할 연속은 한 turn으로 합치고 마지막은 반드시 `user`다.
 */

import type { LlmChatMessage } from "../llm/types";

/** 검색 API 응답의 청크. 응답 순서(점수 내림차순)가 근거 번호다 */
export interface SearchChunk {
  importRunId: number;
  importedPageId: number;
  chunkIndex: number;
  title: string;
  sourceUrl: string;
  content: string;
  score: number;
}

/** `GET /api/v1/conversations/{sessionId}` 응답 항목 중 프롬프트에 필요한 부분 */
export interface ConversationMessage {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
}

const BLOCK_SEPARATOR = "\n\n";

export function buildSystemPrompt(groundingRules: string, chunks: readonly SearchChunk[]): string {
  let prompt = groundingRules;
  chunks.forEach((chunk, index) => {
    prompt +=
      `[근거 문서 ${index + 1}]\n제목: ${chunk.title}\n문서 ID: ${chunk.importedPageId}` +
      `\n문서 링크: ${chunk.sourceUrl}\n내용:\n${chunk.content}\n\n`;
  });
  return prompt;
}

/**
 * 이력을 LLM 메시지로 옮긴다.
 *
 * 이력은 검색 API가 USER를 저장한 뒤 읽으므로 마지막 항목이 현재 질문이어야 한다. 그렇지
 * 않으면(이력이 비었거나 순서가 어긋남) 현재 질문을 `user`로 덧붙인다.
 */
export function toLlmMessages(history: readonly ConversationMessage[], content: string): LlmChatMessage[] {
  const messages: LlmChatMessage[] = [];
  for (const message of history) {
    const role = message.role === "USER" ? "user" : "assistant";
    const last = messages[messages.length - 1];
    if (last !== undefined && last.role === role) {
      last.content += BLOCK_SEPARATOR + message.content;
    } else {
      messages.push({ role, content: message.content });
    }
  }

  const last = messages[messages.length - 1];
  if (last === undefined || last.role !== "user" || last.content !== content) {
    if (last !== undefined && last.role === "user") {
      last.content += BLOCK_SEPARATOR + content;
    } else {
      messages.push({ role: "user", content });
    }
  }
  return messages;
}
