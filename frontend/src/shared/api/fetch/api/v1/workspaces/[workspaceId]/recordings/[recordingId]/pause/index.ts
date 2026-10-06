import {
  PostRecordingPauseResponseDto,
  type PostRecordingPauseRequestDto,
  type PostRecordingPauseResponseRaw,
} from "@api/dto/recording";
import { httpClient } from "@api/httpClient";

export const RECORDING_PAUSE_API_PATH = (
  workspaceId: number,
  recordingId: number,
) => `/api/v1/workspaces/${workspaceId}/recordings/${recordingId}/pause`;

interface PauseRecordingApiParams {
  workspaceId: number;
  recordingId: number;
  body: PostRecordingPauseRequestDto;
}

/**
 * @description 최초 녹음 탭에서 녹음 세션을 일시정지합니다
 * @param params - 워크스페이스 ID·녹음 세션 ID·최초 탭 증명
 * @returns 일시정지 시각과 그때까지의 누적 녹음 시간
 * @example
 * await pauseRecordingApi({ workspaceId: 1, recordingId: 10, body });
 */
export const pauseRecordingApi = async ({
  workspaceId,
  recordingId,
  body,
}: PauseRecordingApiParams) => {
  const response = await httpClient<PostRecordingPauseResponseRaw>({
    method: "post",
    url: RECORDING_PAUSE_API_PATH(workspaceId, recordingId),
    data: body,
  });

  return new PostRecordingPauseResponseDto(response.data);
};
