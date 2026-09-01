import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 워크스페이스 생성 화면(`/workspace/create`)으로 이동하는 도메인 훅.
 */
const useNavigateToWorkspaceCreate = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceCreate = () => {
    navigate(PATH_ROUTE.WORKSPACE_CREATE);
  };

  return { navigateToWorkspaceCreate };
};

export default useNavigateToWorkspaceCreate;
