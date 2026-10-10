import { getDocumentApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]";
import { documentKeys } from "@api/queryKey/document";
import { useQuery } from "@tanstack/react-query";

interface UseDocumentQueryParams {
  workspaceId: number;
  documentId: number;
}

/**
 * 문서 하나의 상세를 조회하는 쿼리 훅.
 *
 * 문서 보기 위젯이 써요. 복사 버튼 · 확인 수 · 확인 버튼은 위젯이 받은 값을 넘겨받아, 문서를 다시 조회하지 않아요.
 *
 * 두 id가 정수인지는 확인하지 않고 그대로 요청해요. 잘못된 id인지는 서버가 판단해 400 · 404로 답해요.
 */
const useDocumentQuery = ({
  workspaceId,
  documentId,
}: UseDocumentQueryParams) => {
  return useQuery({
    queryKey: documentKeys.detail({ workspaceId, documentId }),
    queryFn: () => getDocumentApi({ workspaceId, documentId }),
  });
};

export default useDocumentQuery;
