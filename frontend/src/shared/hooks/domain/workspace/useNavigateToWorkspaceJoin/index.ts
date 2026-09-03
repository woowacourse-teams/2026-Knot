import { getRouterPath } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

import type { WorkspaceJoinState } from "@/shared/types/workspaceJoin";

interface NavigateToWorkspaceJoinParams extends WorkspaceJoinState {
  workspaceId: string;
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 워크스페이스 입장 확인 화면(`/workspace/:workspaceId/join`)으로 이동하는 도메인 훅.
 *
 * 미리보기 응답의 `workspaceId`는 경로에, 참여 API가 받을 `credential`과 카드에 보여줄 `workspaceName`은
 * 라우터 state(`WorkspaceJoinState`)로 넘겨요. 입장 확인 화면은 `useWorkspaceJoinState`로 이 값을 읽어요.
 * `useEffect` 안에서 부르는 곳(초대 링크 게이트)이 있어 참조를 `useCallback`으로 고정해요.
 */
const useNavigateToWorkspaceJoin = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceJoin = useCallback(
    ({
      workspaceId,
      credential,
      workspaceName,
      replace = false,
    }: NavigateToWorkspaceJoinParams) => {
      const state: WorkspaceJoinState = { credential, workspaceName };

      navigate(
        getRouterPath({ routeKey: "WORKSPACE_JOIN", params: { workspaceId } }),
        { replace, state },
      );
    },
    [navigate],
  );

  return { navigateToWorkspaceJoin };
};

export default useNavigateToWorkspaceJoin;
