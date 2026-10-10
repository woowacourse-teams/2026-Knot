import {
  GetDocumentConfirmationsResponseDto,
  type GetDocumentConfirmationsResponseRaw,
} from "@api/dto/document";
import { httpClient } from "@api/httpClient";

export const DOCUMENT_CONFIRMATIONS_API_PATH = (
  workspaceId: number,
  documentId: number,
) => `/api/v1/workspaces/${workspaceId}/documents/${documentId}/confirmations`;

/** 서버가 한 번에 주는 최대 크기. 팀 규모에서는 한 페이지로 충분해 다음 페이지는 받지 않아요 */
const CONFIRMATIONS_PAGE_SIZE = 100;

interface GetDocumentConfirmationsApiParams {
  workspaceId: number;
  documentId: number;
}

/**
 * @description 문서의 확인 대상과 대상별 확인 상태를 조회합니다. 실패는 `401` 미인증 · `403` 워크스페이스 멤버 아님 · `404` 문서 없음으로 구분해요
 * @param params - 워크스페이스 ID·문서 ID
 * @returns 확인 대상 목록(최대 100명)과 확인 집계
 * @example
 * const { items } = await getDocumentConfirmationsApi({ workspaceId: 1, documentId: 101 });
 */
export const getDocumentConfirmationsApi = async ({
  workspaceId,
  documentId,
}: GetDocumentConfirmationsApiParams) => {
  const response = await httpClient<GetDocumentConfirmationsResponseRaw>({
    method: "get",
    url: DOCUMENT_CONFIRMATIONS_API_PATH(workspaceId, documentId),
    params: { size: CONFIRMATIONS_PAGE_SIZE },
  });

  return new GetDocumentConfirmationsResponseDto(response.data);
};
