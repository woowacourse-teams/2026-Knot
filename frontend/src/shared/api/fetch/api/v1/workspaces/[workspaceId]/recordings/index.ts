import {
  PostRecordingResponseDto,
  type PostRecordingRequestDto,
  type PostRecordingResponseRaw,
} from "@api/dto/recording";
import { apiClient } from "@api/axiosInstance/apiClient";

export const RECORDINGS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/recordings`;

interface StartRecordingApiParams {
  workspaceId: number;
  body: PostRecordingRequestDto;
}

/**
 * @description 워크스페이스에서 내 녹음 세션을 시작합니다
 * @param params - 워크스페이스 ID·시작 요청 ID·최초 탭 ID·제어 증명
 * @returns 녹음 세션 ID·상태·서버가 확정한 시작 시각
 * @example
 * const { recordingId } = await startRecordingApi({ workspaceId: 1, body });
 */
export const startRecordingApi = async ({
  workspaceId,
  body,
}: StartRecordingApiParams) => {
  const response = await apiClient<PostRecordingResponseRaw>({
    method: "post",
    url: RECORDINGS_API_PATH(workspaceId),
    data: body,
  });

  return new PostRecordingResponseDto(response.data);
};
