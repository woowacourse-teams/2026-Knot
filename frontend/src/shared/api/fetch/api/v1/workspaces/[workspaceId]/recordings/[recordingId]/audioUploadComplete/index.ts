import {
  PostRecordingAudioUploadCompleteResponseDto,
  type PostRecordingAudioUploadCompleteRequestDto,
  type PostRecordingAudioUploadCompleteResponseRaw,
} from "@api/dto/recording";
import { httpClient } from "@api/httpClient";

export const RECORDING_AUDIO_UPLOAD_COMPLETE_API_PATH = (
  workspaceId: number,
  recordingId: number,
) =>
  `/api/v1/workspaces/${workspaceId}/recordings/${recordingId}/audio-upload-complete`;

interface CompleteRecordingAudioUploadApiParams {
  workspaceId: number;
  recordingId: number;
  body: PostRecordingAudioUploadCompleteRequestDto;
}

/**
 * @description Presigned URL로 PUT을 마친 최종 오디오의 업로드 완료를 서버에 확인받습니다
 * @param params - 워크스페이스 ID·녹음 세션 ID·업로드 예약 ID
 * @returns 업로드 상태와 서버가 확정한 완료 시각
 * @example
 * await completeRecordingAudioUploadApi({ workspaceId: 1, recordingId: 10, body });
 */
export const completeRecordingAudioUploadApi = async ({
  workspaceId,
  recordingId,
  body,
}: CompleteRecordingAudioUploadApiParams) => {
  const response =
    await httpClient<PostRecordingAudioUploadCompleteResponseRaw>({
      method: "post",
      url: RECORDING_AUDIO_UPLOAD_COMPLETE_API_PATH(workspaceId, recordingId),
      data: body,
    });

  return new PostRecordingAudioUploadCompleteResponseDto(response.data);
};
