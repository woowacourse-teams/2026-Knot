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
 *
 * 두 id가 정수인지는 확인하지 않고 그대로 요청해요. 잘못된 id인지는 서버가 판단해 400 · 404로 답해요.
 */
const useDocumentConfirmationsQuery = ({
  workspaceId,
  documentId,
}: UseDocumentConfirmationsQueryParams) => {
  return useQuery({
    queryKey: documentKeys.confirmations({ workspaceId, documentId }),
    queryFn: () => getDocumentConfirmationsApi({ workspaceId, documentId }),
  });
};

export default useDocumentConfirmationsQuery;
