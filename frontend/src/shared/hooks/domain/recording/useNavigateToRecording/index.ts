import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 녹음 화면(`/workspace/:workspaceId/recording`)으로 이동하는 도메인 훅.
 */
const useNavigateToRecording = () => {
  const navigate = useNavigate();

  const navigateToRecording = (workspaceId: string) => {
    navigate(getRouterPath({ routeKey: "RECORDING", params: { workspaceId } }));
  };

  return { navigateToRecording };
};

export default useNavigateToRecording;
