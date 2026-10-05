import {
  PostRecordingAudioUploadUrlRequestDto,
  type PostRecordingAudioUploadUrlRequestInput,
} from "@api/dto/recording";
import { issueRecordingAudioUploadUrlApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/audioUploadUrl";
import { useMutation } from "@tanstack/react-query";

interface IssueRecordingAudioUploadUrlMutationVariables extends PostRecordingAudioUploadUrlRequestInput {
  workspaceId: number;
  recordingId: number;
}

/**
 * 종료한 녹음의 최종 오디오를 올릴 Presigned PUT URL을 발급받는 뮤테이션 훅.
 *
 * 아직 쓰지 않은 예약이 있으면 서버가 같은 `uploadId`로 URL만 다시 발급해요. 무효화할 쿼리는 없어요.
 */
const useIssueRecordingAudioUploadUrlMutation = () => {
  return useMutation({
    mutationFn: ({
      workspaceId,
      recordingId,
      ...input
    }: IssueRecordingAudioUploadUrlMutationVariables) =>
      issueRecordingAudioUploadUrlApi({
        workspaceId,
        recordingId,
        body: new PostRecordingAudioUploadUrlRequestDto(input),
      }),
  });
};

export default useIssueRecordingAudioUploadUrlMutation;
