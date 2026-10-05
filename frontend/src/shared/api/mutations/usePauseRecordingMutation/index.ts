import {
  PostRecordingPauseRequestDto,
  type PostRecordingPauseRequestInput,
} from "@api/dto/recording";
import { pauseRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/pause";
import { useMutation } from "@tanstack/react-query";

interface PauseRecordingMutationVariables extends PostRecordingPauseRequestInput {
  workspaceId: number;
  recordingId: number;
}

/**
 * 최초 녹음 탭에서 녹음 세션을 일시정지하는 뮤테이션 훅.
 *
 * 이미 일시정지였어도 서버가 같은 결과를 돌려줘요. 무효화할 쿼리는 없어요.
 */
const usePauseRecordingMutation = () => {
  return useMutation({
    mutationFn: ({
      workspaceId,
      recordingId,
      ...input
    }: PauseRecordingMutationVariables) =>
      pauseRecordingApi({
        workspaceId,
        recordingId,
        body: new PostRecordingPauseRequestDto(input),
      }),
  });
};

export default usePauseRecordingMutation;
