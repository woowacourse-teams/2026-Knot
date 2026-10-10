import { HTTP_ERROR_TYPE, isHttpError } from "@api/httpClient/error";
import useRetryDocumentGenerationJobMutation from "@api/mutations/useRetryDocumentGenerationJobMutation";
import useRecordingQuery from "@api/queries/useRecordingQuery";
import useRedirectToLoginOnUnauthorized from "@hooks/domain/auth/useRedirectToLoginOnUnauthorized";

import type { RecordingDocumentsViewStatus } from "../types/recordingDocuments";

interface UseRecordingDocumentsParams {
  workspaceId: number;
  recordingId: number;
}

/**
 * 서버의 녹음 상태(`recording.status`)가 어느 화면 상태가 되는지 정한 표.
 *
 * 업로드 확인 전인 `ENDED`도 사용자에게는 `PROCESSING`과 같은 정리 중이에요.
 * 훅이 이 표를 `recording.status`로 읽으므로, 서버 상태가 늘었는데 여기에 없으면 읽는 줄에서 타입 오류가 나요.
 */
const VIEW_STATUS_BY_RECORDING_STATUS = {
  RECORDING: "recording",
  PAUSED: "recording",
  ENDED: "organizing",
  PROCESSING: "organizing",
  COMPLETED: "completed",
  NO_CONTENT: "noContent",
  FAILED: "failed",
} as const satisfies Record<string, RecordingDocumentsViewStatus>;

/** 다시 물어도 결과가 같은 조회 실패(4xx). 이 실패를 받으면 3초마다 하던 조회도 멈춰요 */
const isUnrecoverableLoadError = (error: unknown) =>
  isHttpError(error) && error.isClientError;

/** 다시 시도할 수 없다는 응답. 실패 상태가 아닌 작업(409) · 없는 작업(404) */
const isRetryRejectedError = (error: unknown) =>
  isHttpError(error, HTTP_ERROR_TYPE.conflict) ||
  isHttpError(error, HTTP_ERROR_TYPE.notFound);

/**
 * 정리 화면이 그릴 상태(`viewStatus`)를 정해요. 주소에서 읽은 id를 확인하지 않고 그대로 조회하고, 잘못된 id는 서버 응답(400 · 404)으로 알아요.
 *
 * `viewStatus`는 서버의 녹음 상태(`recording.status`)에 조회 결과와 오류를 더해 해석한 값이에요.
 *
 * - `loading`: 녹음 상태를 아직 받지 못했어요. 401이면 로그인 화면으로 보내는 동안에도 이 상태로 둬요.
 * - `loadFailed`: 녹음 상태를 보여 주지 못해요. `retryLoad`로 다시 조회해요.
 *   400 · 403 · 404처럼 다시 물어도 결과가 같은 실패는 조회가 멈추므로, 앞서 받은 상태가 있어도 이 상태예요.
 *   네트워크 · 서버 문제는 받은 상태가 없을 때만 이 상태예요.
 * - 그 밖에는 받은 녹음 상태를 `VIEW_STATUS_BY_RECORDING_STATUS` 표대로 바꿔요.
 *   `organizing`(정리 중) · `noContent`(문서로 만들 내용 없음) · `failed`(문서를 만들지 못함),
 *   그리고 이 화면에 머물 이유가 없는 `completed`(정리가 끝남) · `recording`(아직 끝내지 않은 녹음)이에요.
 *   `failed`에서 다시 시도할 수 있으면 `retryGeneration`이 있어요.
 *
 * 다시 시도는 문서 만들기 단계의 실패이고 다시 시도할 작업이 있을 때만 할 수 있어요.
 * 전사 · 업로드 실패는 문서 만들기를 다시 해도 결과가 같아 대상이 아니에요.
 * 다시 시도가 거절되면(409 · 404) 더 시도할 수 없어 `retryGeneration`을 없애요.
 * 409는 화면이 아는 녹음 상태와 서버의 녹음 상태가 다를 수 있다는 응답이라, 녹음 상태를 다시 조회해요.
 * 서버에서 이미 정리 중이거나 정리가 끝났으면 그 상태의 화면으로 바뀌어요.
 *
 * 쓰는 쪽이 구조분해해서 받을 수 있게, 어느 상태든 같은 이름의 값을 모두 돌려줘요.
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
    mutate: requestRetry,
    isPending: isRetrying,
    error: retryError,
    variables: lastRetry,
    reset: clearRetryError,
  } = useRetryDocumentGenerationJobMutation();
  const { isUnauthorized } = useRedirectToLoginOnUnauthorized({ error });
  // 다시 시도에서 로그인이 풀려도 같은 방식으로 로그인 화면에 보내요
  useRedirectToLoginOnUnauthorized({ error: retryError });

  const isLoadFailed =
    !isUnauthorized &&
    (isUnrecoverableLoadError(error) || (recording === undefined && isError));

  const getViewStatus = () => {
    if (isLoadFailed) return "loadFailed" as const;
    if (recording === undefined) return "loading" as const;

    return VIEW_STATUS_BY_RECORDING_STATUS[recording.status];
  };

  const viewStatus = getViewStatus();

  // 문서 만들기 단계에서 실패했고 다시 시도할 작업이 있을 때만 그 작업의 id예요
  const retryJobId =
    viewStatus === "failed" && recording?.failureStage === "DOCUMENT_GENERATION"
      ? recording.documentGenerationJobId
      : null;
  // 주소가 다른 녹음으로 바뀌어도 앞 녹음에서 거절된 기록은 남아 있어요. 그래서 거절된 것이 지금 녹음의 작업인지 함께 봐요
  const isRetryRejected =
    isRetryRejectedError(retryError) && lastRetry?.jobId === retryJobId;

  // 화면을 새로고침하지 않고 녹음 상태만 다시 조회해요. 새로고침하면 브라우저 안에만 있는 진행 중인 녹음이 사라져요
  const retryLoad = () => {
    refetch();
  };

  const retryGeneration =
    retryJobId === null || isRetryRejected
      ? undefined
      : () =>
          requestRetry(
            { workspaceId, jobId: retryJobId },
            {
              onError: async (retryFailure) => {
                if (!isHttpError(retryFailure, HTTP_ERROR_TYPE.conflict)) {
                  return;
                }

                const { data: latestRecording } = await refetch();

                // 서버에서는 실패 상태가 아니면 거절 기록을 지워요. 남겨 두면 그 작업이 또 실패했을 때 「다시 시도」가 숨겨져요
                if (
                  latestRecording !== undefined &&
                  latestRecording.status !== "FAILED"
                ) {
                  clearRetryError();
                }
              },
            },
          );

  return { viewStatus, retryLoad, retryGeneration, isRetrying };
};
