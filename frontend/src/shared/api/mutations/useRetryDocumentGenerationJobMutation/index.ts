import { retryDocumentGenerationJobApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documentGenerationJobs/[jobId]/retry";
import { recordingKeys } from "@api/queryKey/recording";
import { useMutation, useQueryClient } from "@tanstack/react-query";

interface RetryDocumentGenerationJobMutationVariables {
  workspaceId: number;
  jobId: number;
}

/**
 * 실패한 문서 생성 작업을 다시 시도하는 뮤테이션 훅.
 *
 * 요청 본문이 없어 ID만 넘겨요.
 * 접수되면 녹음 쿼리를 무효화해요. 다시 받은 녹음 상태가 정리 중으로 바뀌어야 화면이 바뀌고 조회가 다시 시작돼요.
 * 이 훅은 어느 녹음의 작업인지 몰라서 녹음 쿼리 전체를 무효화해요.
 * 무효화가 끝날 때까지 `isPending`이 유지되므로, 그동안 버튼을 다시 누를 수 없게 할 수 있어요.
 */
const useRetryDocumentGenerationJobMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: RetryDocumentGenerationJobMutationVariables) =>
      retryDocumentGenerationJobApi(variables),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: recordingKeys.all }),
  });
};

export default useRetryDocumentGenerationJobMutation;
