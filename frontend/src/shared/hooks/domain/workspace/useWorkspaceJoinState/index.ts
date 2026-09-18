import { useLocation } from "react-router";

import type { WorkspaceJoinState } from "@/shared/types/workspaceJoin";

const isWorkspaceJoinState = (state: unknown): state is WorkspaceJoinState =>
  typeof state === "object" &&
  state !== null &&
  typeof (state as Partial<WorkspaceJoinState>).credential === "string" &&
  typeof (state as Partial<WorkspaceJoinState>).workspaceName === "string";

/**
 * 입장 확인 화면이 라우터 state로 받은 `WorkspaceJoinState`를 읽는 도메인 훅.
 *
 * `useNavigateToWorkspaceJoin`이 넘긴 값만 유효해요. 새로고침·주소 직접 입력처럼 state가 없거나
 * 모양이 다르면 `undefined`를 돌려주고, 어디로 보낼지는 쓰는 쪽이 정해요.
 */
const useWorkspaceJoinState = () => {
  const { state } = useLocation();

  const joinState = isWorkspaceJoinState(state) ? state : undefined;

  return { joinState };
};

export default useWorkspaceJoinState;
