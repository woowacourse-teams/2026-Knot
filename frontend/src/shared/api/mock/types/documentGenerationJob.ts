export interface DocumentGenerationJobRetryResponse {
  jobId: number;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  /** 접수한 뒤의 누적 시도 수 */
  attemptCount: number;
}
