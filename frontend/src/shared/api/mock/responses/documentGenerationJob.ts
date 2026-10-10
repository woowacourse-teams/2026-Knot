import type { DocumentGenerationJobRetryResponse } from "@api/mock/types/documentGenerationJob";

// 녹음 mock에서 문서 만들기에 실패한 녹음(12)의 작업(88)을 처음 다시 시도했을 때의 응답이에요
export const documentGenerationJobRetryResponse = {
  jobId: 88,
  status: "QUEUED",
  attemptCount: 2,
} satisfies DocumentGenerationJobRetryResponse;
