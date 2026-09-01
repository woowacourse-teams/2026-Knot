import { httpClient } from "@api/httpClient";

export const WORKSPACE_CONVERSATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/conversations`;

// 목록 조회와 생성 응답이 같은 모양을 써요
interface ChatSession {
  id: number;
  title: string;
  /** ISO 8601 */
  createdAt: string;
  /** ISO 8601 */
  lastMessageAt: string;
}

type GetChatSessionsApiResponse = ChatSession[];

interface PostChatSessionApiRequest {
  /** 최대 255자 */
  title?: string;
}

type PostChatSessionApiResponse = ChatSession;

interface CreateChatSessionApiParams extends PostChatSessionApiRequest {
  workspaceId: number;
}

/**
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
