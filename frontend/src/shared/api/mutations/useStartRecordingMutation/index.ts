import {
  PostRecordingRequestDto,
  type PostRecordingRequestInput,
} from "@api/dto/recording";
import { startRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings";
import { useMutation } from "@tanstack/react-query";

interface StartRecordingMutationVariables extends PostRecordingRequestInput {
  workspaceId: number;
}

/**
 * 워크스페이스에서 내 녹음 세션을 시작하는 뮤테이션 훅.
 *
 * 같은 `requestId`로 다시 보내면 서버가 새 세션을 만들지 않고 기존 세션을 돌려줘요.
 * 이 응답을 읽는 쿼리가 아직 없어 무효화할 쿼리는 없어요.
 */
const useStartRecordingMutation = () => {
  return useMutation({
    mutationFn: ({ workspaceId, ...input }: StartRecordingMutationVariables) =>
      startRecordingApi({
        workspaceId,
        body: new PostRecordingRequestDto(input),
      }),
  });
};

export default useStartRecordingMutation;
