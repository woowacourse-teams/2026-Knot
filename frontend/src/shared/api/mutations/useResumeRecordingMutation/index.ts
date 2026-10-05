import {
  PostRecordingResumeRequestDto,
  type PostRecordingResumeRequestInput,
} from "@api/dto/recording";
import { resumeRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/resume";
import { useMutation } from "@tanstack/react-query";

interface ResumeRecordingMutationVariables extends PostRecordingResumeRequestInput {
  workspaceId: number;
  recordingId: number;
}

/**
 * 최초 녹음 탭에서 일시정지한 녹음 세션을 이어 가는 뮤테이션 훅.
 *
 * 이미 녹음 중이어도 서버가 같은 결과를 돌려줘요. 무효화할 쿼리는 없어요.
 */
const useResumeRecordingMutation = () => {
  return useMutation({
    mutationFn: ({
      workspaceId,
      recordingId,
      ...input
    }: ResumeRecordingMutationVariables) =>
      resumeRecordingApi({
        workspaceId,
        recordingId,
        body: new PostRecordingResumeRequestDto(input),
      }),
  });
};

export default useResumeRecordingMutation;
