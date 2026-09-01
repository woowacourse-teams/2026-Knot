import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  PostChatSessionRequestDto,
  type PostChatSessionRequestInput,
} from "@api/dto/chatSession";
import { createChatSessionApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/conversations";
import { chatKeys } from "@api/queryKey/chat";


interface UseCreateChatSessionMutationParams {
  workspaceId: number;
}

/**
 * 워크스페이스에 대화 세션을 새로 만듭니다.
 *
 * 성공하면 목록이 낡으므로 세션 목록 쿼리를 무효화해 다시 받아옵니다.
 */
const useCreateChatSessionMutation = ({
  workspaceId,
}: UseCreateChatSessionMutationParams) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: PostChatSessionRequestInput) =>
      createChatSessionApi(workspaceId, new PostChatSessionRequestDto(input)),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: chatKeys.sessions(workspaceId),
      }),
  });
};

export default useCreateChatSessionMutation;
