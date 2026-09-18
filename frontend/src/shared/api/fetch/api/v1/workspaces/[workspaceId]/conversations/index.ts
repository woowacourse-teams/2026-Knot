import {
  GetChatSessionsResponseDto,
  PostChatSessionResponseDto,
  type GetChatSessionsResponseRaw,
  type PostChatSessionRequestDto,
  type PostChatSessionResponseRaw,
} from "@api/dto/chatSession";
import { httpClient } from "@api/httpClient";

export const WORKSPACE_CONVERSATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/conversations`;

/**
 * @description 워크스페이스의 대화 세션 목록을 조회합니다
 * @param workspaceId - 워크스페이스 ID
 * @returns 대화 세션 목록
 * @example
 * const { sessions } = await getChatSessionsApi(1);
 */
export const getChatSessionsApi = async (workspaceId: number) => {
  const response = await httpClient<GetChatSessionsResponseRaw>({
    method: "get",
    url: WORKSPACE_CONVERSATIONS_API_PATH(workspaceId),
  });

  return new GetChatSessionsResponseDto(response.data);
};

/**
 * @description 워크스페이스에 대화 세션을 새로 만듭니다
 * @param workspaceId - 워크스페이스 ID
 * @param body - 대화 세션 생성 요청 본문
 * @returns 생성된 대화 세션
 * @example
 * const { id } = await createChatSessionApi(1, new PostChatSessionRequestDto({ title: "새 대화" }));
 */
export const createChatSessionApi = async (
  workspaceId: number,
  body: PostChatSessionRequestDto,
) => {
  const response = await httpClient<PostChatSessionResponseRaw>({
    method: "post",
    url: WORKSPACE_CONVERSATIONS_API_PATH(workspaceId),
    data: body,
  });

  return new PostChatSessionResponseDto(response.data);
};
