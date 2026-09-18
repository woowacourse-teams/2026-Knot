import { chatMessageStreamResponse } from "@api/mock/responses/chatMessage";
import type { ChatMessage } from "@api/mock/types/chatMessage";

/**
 * 이 화면에서 보낸 질문과 받은 답변. 세션 ID로 묶어 둡니다.
 *
 * 실제 서버는 질문을 LLM에 넘기기 전에, 답변은 다 만든 뒤에 저장합니다. mock도 기억해야
 * 스트리밍이 끝나고 메시지 이력을 다시 받아올 때 방금 주고받은 대화가 그대로 남습니다.
 * 기억하지 않으면 답변이 화면에 잠깐 떴다가 고정 응답으로 덮여 사라져요.
 */
const sentMessages = new Map<string, ChatMessage[]>();

/** 저장된 메시지 ID. 고정 응답의 ID와 겹치지 않도록 mock 응답의 값에서 이어 갑니다 */
let lastMessageId = chatMessageStreamResponse.messageId;

/**
 * @description 그 세션에서 주고받아 저장된 메시지를 돌려줍니다
 * @param sessionId - 대화 세션 ID
 * @returns 저장 순서대로의 메시지 목록. 주고받은 적이 없으면 빈 배열
 * @example
 * HttpResponse.json([...chatMessagesResponse, ...getSentChatMessages("100")]);
 */
export const getSentChatMessages = (sessionId: string) =>
  sentMessages.get(sessionId) ?? [];

interface AppendSentChatMessagesParams {
  sessionId: string;
  question: string;
  answer: string;
}

/**
 * @description 질문과 답변 한 쌍을 그 세션의 이력에 더합니다
 * @param params - 대화 세션 ID, 보낸 질문, 만들어진 답변
 * @returns 저장된 assistant 메시지 ID. complete 이벤트가 알려 주는 값
 * @example
 * const messageId = appendSentChatMessages({ sessionId: "100", question, answer });
 */
export const appendSentChatMessages = ({
  sessionId,
  question,
  answer,
}: AppendSentChatMessagesParams) => {
  const createdAt = new Date().toISOString();

  const userMessage = {
    id: (lastMessageId += 1),
    role: "USER",
    content: question,
    createdAt,
  } satisfies ChatMessage;

  const assistantMessage = {
    id: (lastMessageId += 1),
    role: "ASSISTANT",
    content: answer,
    createdAt,
  } satisfies ChatMessage;

  sentMessages.set(sessionId, [
    ...getSentChatMessages(sessionId),
    userMessage,
    assistantMessage,
  ]);

  return assistantMessage.id;
};

/** 테스트끼리 이력이 새지 않도록 비웁니다. `mockServer.resetHandlers()`는 모듈 상태까지 되돌리지 못해요 */
export const resetSentChatMessages = () => sentMessages.clear();
