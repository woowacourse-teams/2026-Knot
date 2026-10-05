import type { SearchMessage } from "@/shared/types/search";

import type { SearchTurn } from "../types/searchTurn";

/**
 * 평평한 메시지 배열을 화면 단위인 턴(질문 1개 + 답변 1개)으로 묶습니다.
 *
 * 질문을 만나면 새 턴을 열고, 이어지는 첫 답변이 그 턴을 닫습니다.
 * 따라서 아직 답변이 없는 마지막 질문은 답변이 `null`인 턴으로 남고,
 * 열린 턴이 없는 답변은 버립니다.
 *
 * @param messages - `sequence` 오름차순으로 정렬된 메시지 목록
 * @returns 입력 순서를 유지한 턴 목록
 *
 * @example
 * toSearchTurns([userMessage, assistantMessage]);
 * // [{ question: userMessage, answer: assistantMessage }]
 */
export const toSearchTurns = (messages: SearchMessage[]) => {
  const turns: SearchTurn[] = [];

  for (const message of messages) {
    if (message.role === "USER") {
      turns.push({ question: message, answer: null });
      continue;
    }

    const openedTurn = turns[turns.length - 1];
    
    // 열린 턴이 없거나 이미 답변이 있는 턴이면 버립니다
    if (!openedTurn || openedTurn.answer !== null) continue;

    openedTurn.answer = message;
  }

  return turns;
};
