import { useParams } from "react-router";

/**
 * 주소의 `:workspaceId`를 숫자로 바꿔 지금 보고 있는 워크스페이스 ID를 돌려줍니다.
 */
export const useCurrentWorkspaceId = () => {
  const { workspaceId } = useParams();

  return Number(workspaceId);
};
