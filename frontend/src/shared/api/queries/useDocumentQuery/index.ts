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
 * 문서 보기 위젯이 쓰고, 문서 머리·확인 버튼도 같은 키를 써서 요청은 한 번만 나가고 캐시를 나눠 써요.
 *
 * 두 id는 정수여야 해요. 주소에서 읽은 값이 정수인지는 주소를 읽는 쪽(문서 보기 위젯)이 확인하고, 여기서는 다시 검사하지 않아요.
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
