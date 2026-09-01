import type { ChatMessage } from "@/shared/types/chatMessage";
import { httpClient } from "@api/httpClient";

export const CHAT_MESSAGES_API_PATH = (sessionId: number) =>
  `/api/v1/conversations/${sessionId}`;

export type GetChatMessagesApiResponse = ChatMessage[];

/**
 * @description 대화 세션의 메시지 목록을 조회합니다
 * @param sessionId - 대화 세션 ID
 * @returns 메시지 목록
 * @example
 * const messages = await getChatMessagesApi(100);
 */
export const getChatMessagesApi = async (sessionId: number) => {
  const response = await httpClient<GetChatMessagesApiResponse>({
    method: "get",
    url: CHAT_MESSAGES_API_PATH(sessionId),
  });

  return response.data;
};
