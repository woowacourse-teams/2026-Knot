import {
  GetCurrentRecordingResponseDto,
  type GetCurrentRecordingResponseRaw,
} from "@api/dto/recording";
import { httpClient } from "@api/httpClient";

export const CURRENT_RECORDING_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/recordings/current`;

/** 표시할 녹음이 없을 때 서버가 본문 없이 답하는 상태 코드 */
const NO_CONTENT_STATUS = 204;

/**
 * @description 워크스페이스에서 내 화면에 표시할 현재 녹음 한 건을 조회합니다. 표시할 녹음이 없으면 서버가 본문 없이 204로 답해요
 * @param workspaceId - 워크스페이스 ID
 * @returns 현재 녹음의 상태·누적 녹음 시간. 표시할 녹음이 없으면 `null`
 * @example
 * const recording = await getCurrentRecordingApi(1);
 */
export const getCurrentRecordingApi = async (workspaceId: number) => {
  const response = await httpClient<GetCurrentRecordingResponseRaw>({
    method: "get",
    url: CURRENT_RECORDING_API_PATH(workspaceId),
  });

  if (response.status === NO_CONTENT_STATUS) return null;

  return new GetCurrentRecordingResponseDto(response.data);
};
