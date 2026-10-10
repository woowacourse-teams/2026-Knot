import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

interface NavigateToDocumentsParams {
  workspaceId: string;
}

/**
 * 문서 목록 화면(`/workspace/:workspaceId/documents`)으로 이동하는 도메인 훅.
 *
 * 히스토리에 push하므로 뒤로 가기 때 원래 화면으로 돌아와요.
 */
const useNavigateToDocuments = () => {
  const navigate = useNavigate();

  const navigateToDocuments = ({ workspaceId }: NavigateToDocumentsParams) => {
    navigate(getRouterPath({ routeKey: "DOCUMENTS", params: { workspaceId } }));
  };

  return { navigateToDocuments };
};

export default useNavigateToDocuments;
