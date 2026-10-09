import useRetryDocumentGenerationJobMutation from "@api/mutations/useRetryDocumentGenerationJobMutation";
import useRecordingQuery from "@api/queries/useRecordingQuery";

interface UseRecordingDocumentsParams {
  workspaceId: number;
  recordingId: number;
}

/**
 * 정리 화면이 그릴 상태를 정해요.
 *
 * - `loading`: 녹음 상태를 아직 받지 못했어요.
 * - `organizing`: 문서를 정리하는 중(`ENDED` · `PROCESSING`). 업로드 확인 전인 `ENDED`도 사용자에게는 같은 정리 중이에요.
 * - `noContent`: 문서로 만들 내용이 없었어요.
 * - `failed`: 문서를 만들지 못했어요. 다시 시도할 수 있으면 `retry`가 있어요.
 *
 * 다시 시도는 문서 만들기 단계의 실패이고 다시 시도할 작업이 있을 때만 할 수 있어요.
 * 전사 · 업로드 실패는 문서 만들기를 다시 해도 결과가 같아 대상이 아니에요.
 * 다른 화면으로 보내는 상태(`COMPLETED` · `RECORDING` · `PAUSED`)와 조회 실패는 다음 작업에서 다뤄요.
 */
export const useRecordingDocuments = ({
  workspaceId,
  recordingId,
}: UseRecordingDocumentsParams) => {
  const { data: recording } = useRecordingQuery({ workspaceId, recordingId });
  const { mutate: retryDocumentGeneration, isPending: isRetrying } =
    useRetryDocumentGenerationJobMutation();

  if (recording === undefined) return { status: "loading" } as const;

  if (recording.status === "ENDED" || recording.status === "PROCESSING") {
    return { status: "organizing" } as const;
  }

  if (recording.status === "NO_CONTENT") {
    return { status: "noContent" } as const;
  }

  if (recording.status === "FAILED") {
    const retryJobId =
      recording.failureStage === "DOCUMENT_GENERATION"
        ? recording.documentGenerationJobId
        : null;

    return {
      status: "failed",
      isRetrying,
      retry:
        retryJobId === null
          ? undefined
          : () => retryDocumentGeneration({ workspaceId, jobId: retryJobId }),
    } as const;
  }

  return { status: "loading" } as const;
};
