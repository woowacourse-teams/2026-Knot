import {
  GetDocumentsResponseDto,
  type GetDocumentsResponseRaw,
} from "@api/dto/document";
import { httpClient } from "@api/httpClient";

export const DOCUMENTS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/documents`;

interface GetDocumentsApiParams {
  workspaceId: number;
  /** 앞 응답의 `nextCursor`. 없으면 첫 페이지를 받아요 */
  cursor?: string;
  /** 한 번에 받을 문서 수(1~100). 없으면 서버 기본값 50을 써요 */
  size?: number;
}

/**
 * @description 워크스페이스의 문서 목록 한 페이지와 주제 폴더 전체를 조회합니다. 실패는 `400` 잘못된 cursor · size · `401` 미인증 · `403` 워크스페이스 멤버 아님으로 구분해요
 * @param params - 워크스페이스 ID·커서·한 번에 받을 문서 수
 * @returns 주제 폴더 전체·최신순 문서 한 페이지·다음 페이지 커서
 * @example
 * const { topics, items, nextCursor } = await getDocumentsApi({ workspaceId: 1 });
 */
export const getDocumentsApi = async ({
  workspaceId,
  cursor,
  size,
}: GetDocumentsApiParams) => {
  const response = await httpClient<GetDocumentsResponseRaw>({
    method: "get",
    url: DOCUMENTS_API_PATH(workspaceId),
    params: { cursor, size },
  });

  return new GetDocumentsResponseDto(response.data);
};
