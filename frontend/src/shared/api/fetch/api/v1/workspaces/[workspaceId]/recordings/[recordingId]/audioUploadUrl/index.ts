import {
  PostRecordingAudioUploadUrlResponseDto,
  type PostRecordingAudioUploadUrlRequestDto,
  type PostRecordingAudioUploadUrlResponseRaw,
} from "@api/dto/recording";
import { httpClient } from "@api/httpClient";
import axios from "axios";

export const RECORDING_AUDIO_UPLOAD_URL_API_PATH = (
  workspaceId: number,
  recordingId: number,
) =>
  `/api/v1/workspaces/${workspaceId}/recordings/${recordingId}/audio-upload-url`;

interface IssueRecordingAudioUploadUrlApiParams {
  workspaceId: number;
  recordingId: number;
  body: PostRecordingAudioUploadUrlRequestDto;
}

/**
 * @description 종료한 녹음의 최종 오디오를 올릴 Presigned PUT URL을 발급받습니다
 * @param params - 워크스페이스 ID·녹음 세션 ID·올릴 파일의 Content-Type과 크기
 * @returns 업로드 예약 ID·PUT할 URL·만료 시각
 * @example
 * const { uploadUrl } = await issueRecordingAudioUploadUrlApi({ workspaceId: 1, recordingId: 10, body });
 */
export const issueRecordingAudioUploadUrlApi = async ({
  workspaceId,
  recordingId,
  body,
}: IssueRecordingAudioUploadUrlApiParams) => {
  const response = await httpClient<PostRecordingAudioUploadUrlResponseRaw>({
    method: "post",
    url: RECORDING_AUDIO_UPLOAD_URL_API_PATH(workspaceId, recordingId),
    data: body,
  });

  return new PostRecordingAudioUploadUrlResponseDto(response.data);
};

interface UploadRecordingAudioApiParams {
  uploadUrl: string;
  audio: Blob;
}

/**
 * @description 발급받은 Presigned URL로 최종 오디오 파일을 객체 저장소에 직접 올립니다
 * @param params - 발급받은 업로드 URL·올릴 오디오
 * @returns 없음
 * @example
 * await uploadRecordingAudioApi({ uploadUrl, audio });
 */
export const uploadRecordingAudioApi = async ({
  uploadUrl,
  audio,
}: UploadRecordingAudioApiParams) => {
  // 저장소는 우리 API가 아니라 httpClient의 인증 쿠키·CSRF 헤더를 실으면 서명·CORS가 깨져요.
  // Content-Type은 URL을 받을 때 보낸 값과 같아야 해요
  await axios.put(uploadUrl, audio, {
    headers: { "Content-Type": audio.type },
  });
};
