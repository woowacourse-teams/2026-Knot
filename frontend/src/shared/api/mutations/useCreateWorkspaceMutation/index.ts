import {
  PostWorkspaceRequestDto,
  type PostWorkspaceRequestInput,
} from "@api/dto/workspace";
import { createWorkspaceApi } from "@api/fetch/api/v1/workspaces";
import { workspaceKeys } from "@api/queryKey/workspace";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * 워크스페이스를 만드는 뮤테이션 훅.
 *
 * 성공하면 내가 속한 워크스페이스 목록 캐시를 무효화해 새 워크스페이스가 목록에 반영되게 해요.
 */
const useCreateWorkspaceMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: PostWorkspaceRequestInput) =>
      createWorkspaceApi(new PostWorkspaceRequestDto(input)),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: workspaceKeys.list() }),
  });
};

export default useCreateWorkspaceMutation;
