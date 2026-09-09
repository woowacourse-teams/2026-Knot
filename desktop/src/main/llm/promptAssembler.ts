/**
 * 앱 안 구독 호출의 프롬프트 조립 (기획서 6.5 흐름 3, 로드맵 Q62·Q65).
 *
 * - `system` = 서버가 준 `groundingRules` + `[근거 문서 n]` 블록 ≤8. 블록 형식은 서버 SSE 경로의
 *   `SearchContext.groundingPrompt`와 같아 프롬프트 길이가 서버 경로와 같아진다(로드맵 Q33). 본문은 서버가
 *   이미 `max-context-characters`에 맞춰 잘라 줬으므로 여기서 다시 자르지 않는다(Q28).
 * - `messages` = 세션 히스토리(마지막 4개·4,000자, Q62) + 이번 질문. Messages API는 user/assistant가 번갈아
 *   오고 첫 메시지가 user여야 하므로 같은 역할이 이어지면 한 turn으로 합치고 앞머리의 assistant는 버린다
 *   (백엔드 `AnthropicRequestMapper`와 같은 규칙).
 *
 * 순수 함수다. 질문·문서 본문은 로그에 남기지 않는다.
 */

import type { ChatMessageSummary, SearchChunk } from "../chat/knotApi";

/** 히스토리로 넣는 직전 메시지 수·글자 상한(로드맵 Q62 — 현행 검색 질의 조립과 같은 값) */
export const MAX_HISTORY_MESSAGES = 4;
export const MAX_HISTORY_CHARACTERS = 4000;

/** 같은 역할이 이어질 때 본문을 잇는 구분자(백엔드 `BLOCK_SEPARATOR`와 같다) */
const BLOCK_SEPARATOR = "\n\n";

export interface LlmMessage {
  role: "user" | "assistant";
  content: string;
}

/** 현행 `SearchContext.groundingBlock`과 같은 형식 */
export function groundingBlock(index: number, chunk: SearchChunk): string {
  return (
    `[근거 문서 ${index + 1}]\n제목: ${chunk.title}\n문서 ID: ${chunk.importedPageId}\n` +
    `문서 링크: ${chunk.sourceUrl}\n내용:\n${chunk.content}\n\n`
  );
}

export function buildSystemPrompt(groundingRules: string, chunks: SearchChunk[]): string {
  return groundingRules + chunks.map((chunk, index) => groundingBlock(index, chunk)).join("");
}

/**
 * @param history 세션의 모든 메시지(시간순). 이번 질문은 아직 서버에 없다(턴 저장은 답변 뒤, `S2`)
 * @param question 이번 질문
 */
export function buildMessages(history: ChatMessageSummary[], question: string): LlmMessage[] {
  const recent = history.slice(-MAX_HISTORY_MESSAGES);
  // 글자 예산을 넘으면 오래된 것부터 뺀다
  let total = recent.reduce((sum, message) => sum + message.content.length, 0);
  let start = 0;
  while (start < recent.length && total > MAX_HISTORY_CHARACTERS) {
    total -= recent[start]!.content.length;
    start += 1;
  }

  const merged: LlmMessage[] = [];
  for (const message of recent.slice(start)) {
    const role: LlmMessage["role"] = message.role === "USER" ? "user" : "assistant";
    // 첫 메시지는 user여야 한다
    if (merged.length === 0 && role === "assistant") continue;
    const last = merged[merged.length - 1];
    if (last !== undefined && last.role === role) {
      last.content = `${last.content}${BLOCK_SEPARATOR}${message.content}`;
      continue;
    }
    merged.push({ role, content: message.content });
  }

  const last = merged[merged.length - 1];
  if (last !== undefined && last.role === "user") {
    // 답변 없이 남은 직전 질문(만료된 턴)은 이번 질문과 한 turn으로 합친다
    last.content = `${last.content}${BLOCK_SEPARATOR}${question}`;
  } else {
    merged.push({ role: "user", content: question });
  }
  return merged;
}
