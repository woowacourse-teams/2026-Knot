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
 * 두 id는 정수여야 해요. 주소에서 읽은 값이 정수인지는 주소를 읽는 쪽(문서 보기 위젯)이 확인하고, 여기서는 다시 검사하지 않아요.
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
