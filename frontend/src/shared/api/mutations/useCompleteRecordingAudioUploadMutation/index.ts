import {
  PostRecordingAudioUploadCompleteRequestDto,
  type PostRecordingAudioUploadCompleteRequestInput,
} from "@api/dto/recording";
import { completeRecordingAudioUploadApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/audioUploadComplete";
import { useMutation } from "@tanstack/react-query";

interface CompleteRecordingAudioUploadMutationVariables extends PostRecordingAudioUploadCompleteRequestInput {
  workspaceId: number;
  recordingId: number;
}

/**
 * 최종 오디오 PUT을 마친 뒤 서버에 업로드 완료를 확인받는 뮤테이션 훅.
 *
 * 서버가 저장소의 파일 형식·크기를 확인하고 완료를 기록해요. 같은 `uploadId`로 다시 불러도
 * 같은 결과를 돌려줘요. 무효화할 쿼리는 없어요.
 */
const useCompleteRecordingAudioUploadMutation = () => {
  return useMutation({
    mutationFn: ({
      workspaceId,
      recordingId,
      ...input
    }: CompleteRecordingAudioUploadMutationVariables) =>
      completeRecordingAudioUploadApi({
        workspaceId,
        recordingId,
        body: new PostRecordingAudioUploadCompleteRequestDto(input),
      }),
  });
};

export default useCompleteRecordingAudioUploadMutation;
