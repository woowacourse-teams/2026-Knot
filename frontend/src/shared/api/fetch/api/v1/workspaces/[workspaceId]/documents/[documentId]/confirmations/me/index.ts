import {
  PutDocumentConfirmationResponseDto,
  type PutDocumentConfirmationResponseRaw,
} from "@api/dto/document";
import { httpClient } from "@api/httpClient";

export const DOCUMENT_MY_CONFIRMATION_API_PATH = (
  workspaceId: number,
  documentId: number,
) =>
  `/api/v1/workspaces/${workspaceId}/documents/${documentId}/confirmations/me`;

interface ConfirmDocumentApiParams {
  workspaceId: number;
  documentId: number;
}

/**
 * @description 로그인한 멤버가 문서를 확인했다고 기록합니다. 이미 확인했어도 같은 결과를 돌려주고, 확인 대상이 아니면 `409 CONFIRMATION_NOT_REQUIRED`예요
 * @param params - 워크스페이스 ID·문서 ID
 * @returns 처음 확인한 시각과 확인 뒤의 확인 집계
 * @example
 * const { confirmationSummary } = await confirmDocumentApi({ workspaceId: 1, documentId: 101 });
 */
export const confirmDocumentApi = async ({
  workspaceId,
  documentId,
}: ConfirmDocumentApiParams) => {
  const response = await httpClient<PutDocumentConfirmationResponseRaw>({
    method: "put",
    url: DOCUMENT_MY_CONFIRMATION_API_PATH(workspaceId, documentId),
  });

  return new PutDocumentConfirmationResponseDto(response.data);
};
