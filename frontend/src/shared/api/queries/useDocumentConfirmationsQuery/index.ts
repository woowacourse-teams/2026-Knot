import { getDocumentConfirmationsApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]/confirmations";
import { documentKeys } from "@api/queryKey/document";
import { useQuery } from "@tanstack/react-query";

interface UseDocumentConfirmationsQueryParams {
  workspaceId: number;
  documentId: number;
}

/**
 * 문서의 확인 대상과 대상별 확인 상태를 조회하는 쿼리 훅.
 *
 * 확인한 사람 팝오버가 써요. 확인 수는 문서 상세 응답에 있으므로 이 훅으로 세지 않아요.
 * 라우트 파라미터를 `Number`로 바꾼 값이 정수가 아니면 요청하지 않아요(문서 상세와 같은 기준).
 */
const useDocumentConfirmationsQuery = ({
  workspaceId,
  documentId,
}: UseDocumentConfirmationsQueryParams) => {
  return useQuery({
    queryKey: documentKeys.confirmations({ workspaceId, documentId }),
    queryFn: () => getDocumentConfirmationsApi({ workspaceId, documentId }),
    enabled: Number.isInteger(workspaceId) && Number.isInteger(documentId),
  });
};

export default useDocumentConfirmationsQuery;
