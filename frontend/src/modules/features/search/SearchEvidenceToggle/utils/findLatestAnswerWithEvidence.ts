import type { SearchMessage } from "@/shared/types/search";

/**
 * 근거 문서가 하나 이상 붙은 답변(`ASSISTANT`) 중 가장 최근 것을 찾습니다.
 *
 * 질문(`USER`)과 근거 없는 답변은 건너뛰며, 입력 배열은 바꾸지 않습니다.
 *
 * @param messages - `sequence` 오름차순으로 정렬된 메시지 목록
 * @returns 근거 있는 가장 최근 답변. 없으면 `undefined`
 *
 * @example
 * findLatestAnswerWithEvidence([answerWithEvidence, question, answerWithoutEvidence]);
 * // answerWithEvidence
 */
export const findLatestAnswerWithEvidence = (messages: SearchMessage[]) => {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];

    if (message.role === "ASSISTANT" && message.evidences.length > 0) {
      return message;
    }
  }

  return undefined;
};
