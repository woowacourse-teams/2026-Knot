import {
  PutLastViewedWorkspaceRequestDto,
  type PutLastViewedWorkspaceRequestInput,
} from "@api/dto/workspace";
import { updateLastViewedWorkspaceApi } from "@api/fetch/api/v1/members/me/lastViewedWorkspace";
import { useMutation } from "@tanstack/react-query";

/**
 * 마지막으로 본 워크스페이스를 갱신하는 뮤테이션 훅.
 *
 * ADR 265대로 워크스페이스 조회에 성공한 뒤 한 번 부르고, 실패해도 진입을 막지 않으므로
 * 쓰는 쪽(`useWorkspaceEntry`)은 결과를 보지 않아요.
 * 이 값을 읽는 목록 조회(`GET /workspaces`) 쿼리 훅이 아직 없어 여기서 무효화할 쿼리는 없어요.
 */
const useUpdateLastViewedWorkspaceMutation = () => {
  return useMutation({
    mutationFn: (input: PutLastViewedWorkspaceRequestInput) =>
      updateLastViewedWorkspaceApi(new PutLastViewedWorkspaceRequestDto(input)),
  });
};

export default useUpdateLastViewedWorkspaceMutation;
