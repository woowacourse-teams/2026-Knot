import type { ChatSession } from "@/shared/types/chatSession";
import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 워크스페이스 대화 세션 목록 조회·생성 API 경로를 생성하는 함수
 * @param workspaceId - 워크스페이스 ID
 * @returns API 경로 문자열
 */
export const WORKSPACE_CONVERSATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/conversations`;

/**
 * @public
 * @category Types
 * @description 대화 세션 목록 조회 응답 타입
 */
export type GetChatSessionsApiResponse = ChatSession[];

/**
 * @public
 * @category Types
 * @interface PostChatSessionApiRequest
 * @description 대화 세션 생성 요청 타입
 * @property {string} [title] - 세션 제목 (최대 255자)
 */
export interface PostChatSessionApiRequest {
  title?: string;
}

/**
 * @public
 * @category Types
 * @description 대화 세션 생성 응답 타입. 생성된 세션
 */
export type PostChatSessionApiResponse = ChatSession;

/**
 * @public
 * @category Types
 * @interface CreateChatSessionApiParams
 * @description 대화 세션 생성 함수 인자
 * @property {number} workspaceId - 워크스페이스 ID
 * @property {string} [title] - 세션 제목
 */
export interface CreateChatSessionApiParams extends PostChatSessionApiRequest {
  workspaceId: number;
}

/**
 * @public
 * @category Conversations
 * @description 워크스페이스의 대화 세션 목록을 조회합니다
 * @param workspaceId - 워크스페이스 ID
 * @returns 대화 세션 목록
 * @example
 * const sessions = await getChatSessionsApi(1);
 */
export const getChatSessionsApi = async (workspaceId: number) => {
  const response = await httpClient<GetChatSessionsApiResponse>({
    method: "get",
    url: WORKSPACE_CONVERSATIONS_API_PATH(workspaceId),
  });

  return response.data;
};

/**
 * @public
 * @category Conversations
 * @description 워크스페이스에 대화 세션을 새로 만듭니다
 * @param params - 워크스페이스 ID와 세션 제목
 * @returns 생성된 대화 세션
 * @example
 * const session = await createChatSessionApi({ workspaceId: 1, title: "새 대화" });
 */
export const createChatSessionApi = async ({
  workspaceId,
  title,
}: CreateChatSessionApiParams) => {
  const response = await httpClient<PostChatSessionApiResponse>({
    method: "post",
    url: WORKSPACE_CONVERSATIONS_API_PATH(workspaceId),
    data: { title },
  });

  return response.data;
};
