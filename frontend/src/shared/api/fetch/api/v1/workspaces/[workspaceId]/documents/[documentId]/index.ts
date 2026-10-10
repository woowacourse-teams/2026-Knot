import {
  GetDocumentResponseDto,
  type GetDocumentResponseRaw,
} from "@api/dto/document";
import { httpClient } from "@api/httpClient";

export const DOCUMENT_API_PATH = (workspaceId: number, documentId: number) =>
  `/api/v1/workspaces/${workspaceId}/documents/${documentId}`;

interface GetDocumentApiParams {
  workspaceId: number;
  documentId: number;
}

/**
 * @description 문서 하나의 상세 정보와 본문을 조회합니다. 실패는 `401` 미인증 · `403` 워크스페이스 멤버 아님 · `404` 문서 없음으로 구분해요
 * @param params - 워크스페이스 ID·문서 ID
 * @returns 문서 제목·Markdown 본문·내 확인 상태·확인 집계
 * @example
 * const { title, content } = await getDocumentApi({ workspaceId: 1, documentId: 101 });
 */
export const getDocumentApi = async ({
  workspaceId,
  documentId,
}: GetDocumentApiParams) => {
  const response = await httpClient<GetDocumentResponseRaw>({
    method: "get",
    url: DOCUMENT_API_PATH(workspaceId, documentId),
  });

  return new GetDocumentResponseDto(response.data);
};
