import { getRouterPath } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

interface NavigateToDocumentParams {
  workspaceId: number;
  documentId: number;
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 문서 보기 화면(`/workspace/:workspaceId/documents/:documentId`)으로 이동하는 도메인 훅.
 *
 * 기본은 히스토리에 push해서 뒤로 가기 때 원래 화면으로 돌아와요.
 * 녹음 뒤 문서 정리 화면처럼 뒤로 가기로 다시 볼 이유가 없는 곳에서 넘어올 때는 `replace`를 켜요.
 * `useEffect` 안에서 부르는 곳(정리 화면)이 있어 참조를 `useCallback`으로 고정해요.
 */
const useNavigateToDocument = () => {
  const navigate = useNavigate();

  const navigateToDocument = useCallback(
    ({
      workspaceId,
      documentId,
      replace = false,
    }: NavigateToDocumentParams) => {
      navigate(
        getRouterPath({
          routeKey: "DOCUMENT",
          params: {
            workspaceId: String(workspaceId),
            documentId: String(documentId),
          },
        }),
        { replace },
      );
    },
    [navigate],
  );

  return { navigateToDocument };
};

export default useNavigateToDocument;
