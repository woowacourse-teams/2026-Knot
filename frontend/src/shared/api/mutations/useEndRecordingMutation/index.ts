import { endRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/end";
import { useMutation } from "@tanstack/react-query";

interface EndRecordingMutationVariables {
  workspaceId: number;
  recordingId: number;
}

/**
 * 내 녹음 세션을 종료하는 뮤테이션 훅.
 *
 * 요청 본문이 없어 ID만 넘겨요. 종료 성공은 업로드 완료를 뜻하지 않아,
 * 최종 오디오는 이어서 업로드 URL을 받아 올려야 해요. 무효화할 쿼리는 없어요.
 */
const useEndRecordingMutation = () => {
  return useMutation({
    mutationFn: (variables: EndRecordingMutationVariables) =>
      endRecordingApi(variables),
  });
};

export default useEndRecordingMutation;
