import { HTTP_ERROR_TYPE, isHttpError } from "@api/httpClient/error";
import useRetryDocumentGenerationJobMutation from "@api/mutations/useRetryDocumentGenerationJobMutation";
import useRecordingQuery from "@api/queries/useRecordingQuery";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import useRedirectToLoginOnUnauthorized from "@hooks/domain/auth/useRedirectToLoginOnUnauthorized";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";

interface UseRecordingDocumentsParams {
  workspaceId: number;
  recordingId: number;
}

/** 다시 시도할 수 없다는 응답. 실패 상태가 아닌 작업(409) · 없는 작업(404) */
const isRetryRejectedError = (error: unknown) =>
  isHttpError(error, HTTP_ERROR_TYPE.conflict) ||
  isHttpError(error, HTTP_ERROR_TYPE.notFound);

/**
 * 정리 화면이 그릴 상태를 정해요. 주소의 id는 위젯이 읽은 자리에서 확인하므로 여기서는 정수만 받아요.
 *
 * - `loading`: 녹음 상태를 아직 받지 못했어요. 401이면 로그인 화면으로 보내는 동안에도 이 상태로 둬요.
 * - `loadFailed`: 녹음 상태를 한 번도 받지 못했어요. 「다시 시도」로 다시 조회해요.
 *   네트워크 · 서버 문제면 3초마다 저절로 다시 조회하므로, 연결이 돌아오면 다른 상태로 바뀌어요.
 * - `organizing`: 문서를 정리하는 중(`ENDED` · `PROCESSING`). 업로드 확인 전인 `ENDED`도 사용자에게는 같은 정리 중이에요.
 * - `noContent`: 문서로 만들 내용이 없었어요.
 * - `failed`: 문서를 만들지 못했어요. 다시 시도할 수 있으면 `retry`가 있어요.
 * - `completed`: 정리가 끝났어요. 이 화면에 머물 이유가 없어요.
 * - `recording`: 아직 끝내지 않은 녹음이에요(`RECORDING` · `PAUSED`).
 *
 * 다시 시도는 문서 만들기 단계의 실패이고 다시 시도할 작업이 있을 때만 할 수 있어요.
 * 전사 · 업로드 실패는 문서 만들기를 다시 해도 결과가 같아 대상이 아니에요.
 * 다시 시도가 거절되면(409 · 404) 더 시도할 수 없어 `retry`를 없애요.
 */
export const useRecordingDocuments = ({
  workspaceId,
  recordingId,
}: UseRecordingDocumentsParams) => {
  const {
    data: recording,
    error,
    isError,
    refetch,
  } = useRecordingQuery({ workspaceId, recordingId });
  const {
    mutate: retryDocumentGeneration,
    isPending: isRetrying,
    error: retryError,
  } = useRetryDocumentGenerationJobMutation();
  const { isUnauthorized } = useRedirectToLoginOnUnauthorized({ error });
  const { navigateToLogin } = useNavigateToLogin();

  if (recording === undefined) {
    if (isError && !isUnauthorized) {
      return {
        status: "loadFailed",
        retry: () => {
          refetch();
        },
      } as const;
    }

    return { status: "loading" } as const;
  }

  if (recording.status === "ENDED" || recording.status === "PROCESSING") {
    return { status: "organizing" } as const;
  }

  if (recording.status === "NO_CONTENT") {
    return { status: "noContent" } as const;
  }

  if (recording.status === "COMPLETED") {
    return { status: "completed" } as const;
  }

  if (recording.status === "FAILED") {
    const retryJobId =
      recording.failureStage === "DOCUMENT_GENERATION"
        ? recording.documentGenerationJobId
        : null;

    if (retryJobId === null || isRetryRejectedError(retryError)) {
      return { status: "failed", isRetrying, retry: undefined } as const;
    }

    return {
      status: "failed",
      isRetrying,
      retry: () =>
        retryDocumentGeneration(
          { workspaceId, jobId: retryJobId },
          {
            onError: (retryFailure) => {
              if (isUnauthorizedError(retryFailure)) {
                navigateToLogin({ replace: true });
              }
            },
          },
        ),
    } as const;
  }

  return { status: "recording" } as const;
};
