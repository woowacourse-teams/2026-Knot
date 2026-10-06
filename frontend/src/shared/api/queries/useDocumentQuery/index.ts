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
 * 라우트 파라미터를 `Number`로 바꾼 값이 정수가 아니면(`/documents/abc` 같은 잘못된 주소) 요청하지 않아요.
 * `NaN`이 그대로 가면 `/documents/NaN`으로 요청이 나가고 캐시 키도 `null`로 뭉개져요.
 */
const useDocumentQuery = ({
  workspaceId,
  documentId,
}: UseDocumentQueryParams) => {
  return useQuery({
    queryKey: documentKeys.detail({ workspaceId, documentId }),
    queryFn: () => getDocumentApi({ workspaceId, documentId }),
    enabled: Number.isInteger(workspaceId) && Number.isInteger(documentId),
  });
};

export default useDocumentQuery;
