import {
  PostDocumentGenerationJobRetryResponseDto,
  type PostDocumentGenerationJobRetryResponseRaw,
} from "@api/dto/documentGenerationJob";
import { httpClient } from "@api/httpClient";

export const DOCUMENT_GENERATION_JOB_RETRY_API_PATH = (
  workspaceId: number,
  jobId: number,
) =>
  `/api/v1/workspaces/${workspaceId}/document-generation-jobs/${jobId}/retry`;

interface RetryDocumentGenerationJobApiParams {
  workspaceId: number;
  jobId: number;
}

/**
 * @description 실패한 문서 생성 작업을 다시 시도합니다. 저장된 원문으로 문서 만들기만 다시 하고, 녹음을 다시 전사하지는 않아요
 * @param params - 워크스페이스 ID·문서 생성 작업 ID(녹음 상세의 documentGenerationJobId)
 * @returns 다시 접수한 작업의 상태와 누적 시도 수
 * @example
 * await retryDocumentGenerationJobApi({ workspaceId: 1, jobId: 88 });
 */
export const retryDocumentGenerationJobApi = async ({
  workspaceId,
  jobId,
}: RetryDocumentGenerationJobApiParams) => {
  const response = await httpClient<PostDocumentGenerationJobRetryResponseRaw>({
    method: "post",
    url: DOCUMENT_GENERATION_JOB_RETRY_API_PATH(workspaceId, jobId),
  });

  return new PostDocumentGenerationJobRetryResponseDto(response.data);
};
