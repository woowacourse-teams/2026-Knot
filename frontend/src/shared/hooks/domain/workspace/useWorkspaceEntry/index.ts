import useUpdateLastViewedWorkspaceMutation from "@api/mutations/useUpdateLastViewedWorkspaceMutation";
import useWorkspaceQuery from "@api/queries/useWorkspaceQuery";
import useNavigateToWorkspace from "@hooks/domain/workspace/useNavigateToWorkspace";
import useWorkspaceAccessGuard from "@hooks/domain/workspace/useWorkspaceAccessGuard";
import { useEffect, useRef } from "react";

interface UseWorkspaceEntryParams {
  /** 라우트 파라미터를 `Number`로 바꾼 값. 정수가 아니면 없는 워크스페이스로 봐요 */
  workspaceId: number;
}

/**
 * 워크스페이스 라우트(`/workspace/:workspaceId/*`)에 들어갈 수 있는지 한 곳에서 판정하는 도메인 훅.
 *
 * 워크스페이스 조회로 판정해요.
 * - 성공: `isReady`가 `true`가 되고, 마지막으로 본 워크스페이스를 한 번 갱신해요(ADR 265). 갱신 실패는 진입을 막지 않아요.
 * - 401: 로그인 화면으로, 403·404: 선택 화면(`/workspace`)으로 `replace` 이동해요(`useWorkspaceAccessGuard`).
 * - 정수가 아닌 workspaceId: 조회할 수 없으니 404처럼 선택 화면으로 보내요.
 *
 * 그 외 실패(네트워크·5xx·timeout)는 이동하지 않고 `isReady`만 `false`로 남아요.
 * 홈뿐 아니라 하위 라우트(탐색 등)도 같은 레이아웃 아래라 진입마다 같은 판정을 거쳐요.
 *
 * 갱신은 워크스페이스마다 한 번이에요. 같은 워크스페이스 안에서 재조회되거나 StrictMode가 effect를
 * 두 번 돌려도 반복하지 않고, 다른 워크스페이스로 옮기거나 레이아웃을 새로 마운트하면 다시 갱신해요.
 */
const useWorkspaceEntry = ({ workspaceId }: UseWorkspaceEntryParams) => {
  const isValidWorkspaceId = Number.isInteger(workspaceId);
  const { isSuccess, error } = useWorkspaceQuery({ workspaceId });
  const { mutate: updateLastViewedWorkspace } =
    useUpdateLastViewedWorkspaceMutation();
  const { navigateToWorkspace } = useNavigateToWorkspace();
  const recordedWorkspaceIdRef = useRef<number>(null);

  useWorkspaceAccessGuard({ error });

  useEffect(() => {
    if (isValidWorkspaceId) return;

    navigateToWorkspace({ replace: true });
  }, [isValidWorkspaceId, navigateToWorkspace]);

  useEffect(() => {
    if (!isSuccess || recordedWorkspaceIdRef.current === workspaceId) return;

    recordedWorkspaceIdRef.current = workspaceId;
    updateLastViewedWorkspace({ workspaceId });
  }, [isSuccess, workspaceId, updateLastViewedWorkspace]);

  return { isReady: isSuccess };
};

export default useWorkspaceEntry;
