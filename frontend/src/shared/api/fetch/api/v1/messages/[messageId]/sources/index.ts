import {
  GetChatMessageSourcesResponseDto,
  type GetChatMessageSourcesResponseRaw,
} from "@api/dto/chatMessage";
import { httpClient } from "@api/httpClient";

export const CHAT_MESSAGE_SOURCES_API_PATH = (messageId: number) =>
  `/api/v1/messages/${messageId}/sources`;

/**
 * @description 답변 메시지의 근거가 된 검색 출처(청크 단위, 최대 8건)를 관련도 순으로 조회합니다
 * @param messageId - 출처를 조회할 답변(ASSISTANT) 메시지 ID
 * @returns 관련도 순 검색 출처 목록
 * @example
 * const { searchReferences } = await getChatMessageSourcesApi(1002);
 */
export const getChatMessageSourcesApi = async (messageId: number) => {
  const response = await httpClient<GetChatMessageSourcesResponseRaw>({
    method: "get",
    url: CHAT_MESSAGE_SOURCES_API_PATH(messageId),
  });

  return new GetChatMessageSourcesResponseDto(response.data);
};
