import useNavigateToWorkspace from "@hooks/domain/workspace/useNavigateToWorkspace";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import { useParams } from "react-router";

import DocumentContent from "./ui/DocumentContent";
import DocumentNotFound from "./ui/DocumentNotFound";

/**
 * 문서 보기 섹션. 주소의 문서를 불러와 제목과 본문을 보여줘요.
 *
 * 주소의 두 id는 읽은 이 자리에서 확인해요. 정수가 아니면 조회를 시작하지 않고 문서를 찾을 수 없다고 알려요.
 * 그래서 조회하는 쪽(`ui/DocumentContent`와 그 아래)은 정수만 받고 다시 검사하지 않아요.
 */
export default function DocumentViewer() {
  const params = useParams();
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const { navigateToWorkspace } = useNavigateToWorkspace();

  const workspaceId = Number(params.workspaceId);
  const documentId = Number(params.documentId);
  const isValidAddress =
    Number.isInteger(workspaceId) && Number.isInteger(documentId);

  const handleGoHome = () => {
    // 주소에 워크스페이스 id가 없으면 홈 주소를 만들 수 없어 워크스페이스 선택 화면으로 보내요
    if (params.workspaceId === undefined) {
      navigateToWorkspace();
      return;
    }

    navigateToWorkspaceHome({ workspaceId: params.workspaceId });
  };

  if (!isValidAddress) {
    return <DocumentNotFound onGoHome={handleGoHome} />;
  }

  return (
    <DocumentContent
      workspaceId={workspaceId}
      documentId={documentId}
      onGoHome={handleGoHome}
    />
  );
}
