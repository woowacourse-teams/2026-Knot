import {
  PostRecordingResumeResponseDto,
  type PostRecordingResumeRequestDto,
  type PostRecordingResumeResponseRaw,
} from "@api/dto/recording";
import { apiClient } from "@api/axiosInstance/apiClient";

export const RECORDING_RESUME_API_PATH = (
  workspaceId: number,
  recordingId: number,
) => `/api/v1/workspaces/${workspaceId}/recordings/${recordingId}/resume`;

interface ResumeRecordingApiParams {
  workspaceId: number;
  recordingId: number;
  body: PostRecordingResumeRequestDto;
}

/**
 * @description 최초 녹음 탭에서 일시정지한 녹음 세션을 이어 갑니다
 * @param params - 워크스페이스 ID·녹음 세션 ID·최초 탭 증명
 * @returns 재개 시각과 재개 직전까지의 누적 녹음 시간
 * @example
 * await resumeRecordingApi({ workspaceId: 1, recordingId: 10, body });
 */
export const resumeRecordingApi = async ({
  workspaceId,
  recordingId,
  body,
}: ResumeRecordingApiParams) => {
  const response = await apiClient<PostRecordingResumeResponseRaw>({
    method: "post",
    url: RECORDING_RESUME_API_PATH(workspaceId, recordingId),
    data: body,
  });

  return new PostRecordingResumeResponseDto(response.data);
};
