import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 초대 코드 참여 화면(`/workspace/code`)으로 이동하는 도메인 훅.
 */
const useNavigateToWorkspaceCode = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceCode = () => {
    navigate(PATH_ROUTE.WORKSPACE_CODE);
  };

  return { navigateToWorkspaceCode };
};

export default useNavigateToWorkspaceCode;
