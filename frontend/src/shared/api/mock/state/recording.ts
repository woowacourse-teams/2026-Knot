import { documentGenerationJobRetryResponse } from "@api/mock/responses/documentGenerationJob";
import { recordingDetailsResponse } from "@api/mock/responses/recording";
import type { DocumentGenerationJobRetryResponse } from "@api/mock/types/documentGenerationJob";
import type { RecordingDetailResponse } from "@api/mock/types/recording";

// 다시 시도를 접수한 문서 생성 작업의 ID.
// 기본 응답(responses)은 테스트의 기대값으로도 쓰여서 바꾸지 않고, 바뀐 것만 여기에 적어요
const retriedJobIds = new Set<number>();

/** 다시 시도한 기록을 지워 기본 응답 상태로 되돌려요. 테스트와 스토리 사이에 불러요 */
export const resetRecordingMockState = () => {
  retriedJobIds.clear();
};

/** 녹음 상세. 문서 만들기를 다시 시도한 녹음은 다시 정리 중인 상태로 돌려줘요 */
export const findRecordingDetail = (recordingId: number) => {
  const recording = recordingDetailsResponse.find(
    (response) => response.recordingId === recordingId,
  );

  if (
    recording === undefined ||
    recording.documentGenerationJobId === null ||
    !retriedJobIds.has(recording.documentGenerationJobId)
  ) {
    return recording;
  }

  // 다시 시도를 접수하면 서버는 이전 실패 사유를 지우고 지금의 작업 상태를 돌려줘요
  return {
    ...recording,
    status: "PROCESSING",
    documentGenerationStatus: "QUEUED",
    failureStage: null,
    failureReason: null,
  } satisfies RecordingDetailResponse;
};

/**
 * 문서 생성 작업의 다시 시도를 기록해요.
 * 없는 작업이면 `notFound`, 실패 상태가 아니면(진행 중 · 완료 · 이미 다시 시도함) `notAllowed`를 돌려줘요(서버와 같은 기준).
 */
export const retryDocumentGenerationJob = (jobId: number) => {
  const recording = recordingDetailsResponse.find(
    ({ documentGenerationJobId }) => documentGenerationJobId === jobId,
  );

  if (recording === undefined) return { result: "notFound" } as const;

  const current = findRecordingDetail(recording.recordingId);

  if (current?.documentGenerationStatus !== "FAILED") {
    return { result: "notAllowed" } as const;
  }

  retriedJobIds.add(jobId);

  return {
    result: "accepted",
    response: {
      ...documentGenerationJobRetryResponse,
      jobId,
    } satisfies DocumentGenerationJobRetryResponse,
  } as const;
};
