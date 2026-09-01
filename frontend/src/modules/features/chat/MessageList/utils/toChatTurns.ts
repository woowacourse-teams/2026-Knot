import type { ChatMessage } from "../types/chatMessage";
import type { ChatTurn } from "../types/chatTurn";

/**
 * 평평한 메시지 배열을 화면 단위인 턴(질문 1개 + 답변 1개)으로 묶습니다.
 *
 * 질문을 만나면 새 턴을 열고, 이어지는 첫 답변이 그 턴을 닫습니다.
 * 따라서 아직 답변이 없는 마지막 질문은 `pending` 턴으로 남고,
 * 열린 턴이 없는 답변은 버립니다.
 *
 * @param messages - 생성 시각 오름차순으로 정렬된 메시지 목록
 * @returns 입력 순서를 유지한 턴 목록
 *
 * @example
 * toChatTurns([userMessage, assistantMessage]);
 * // [{ id: 1, question: "...", answer: "...", status: "done" }]
 */
export const toChatTurns = (messages: ChatMessage[]) => {
  const turns: ChatTurn[] = [];

  for (const { id, role, content } of messages) {
    // 질문을 만났을 때
    if (role === "USER") {
      turns.push({ id, question: content, answer: null, status: "pending" });
      continue;
    }

    // 답변을 만났을 때
    const openedTurn = turns[turns.length - 1];
    if (!openedTurn || openedTurn.status === "done") continue; // 열린 턴이 없는 답변은 버립니다

    openedTurn.answer = content;
    openedTurn.status = "done";
  }

  return turns;
};
