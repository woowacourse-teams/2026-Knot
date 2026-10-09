import { getDocumentApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]";
import { documentKeys } from "@api/queryKey/document";
import { useQuery } from "@tanstack/react-query";

/** 문서를 받은 뒤 이 시간 동안은 새로 그려지는 컴포넌트가 같은 문서를 다시 요청하지 않아요 */
const DOCUMENT_STALE_TIME_MS = 30 * 1000;

interface UseDocumentQueryParams {
  workspaceId: number;
  documentId: number;
}

/**
 * 문서 하나의 상세를 조회하는 쿼리 훅.
 *
 * 문서 보기 위젯이 쓰고, 복사 버튼 · 확인 수 · 확인 버튼도 같은 키를 써서 캐시를 나눠 써요.
 * 이 컴포넌트들은 위젯이 문서를 받은 뒤에 그려져요. 받은 응답을 곧바로 오래된 것으로 보면 그때 같은 문서를 한 번 더 요청하므로,
 * `staleTime`을 둬서 요청이 한 번만 나가게 해요. 확인 요청 뒤의 캐시 무효화는 이 시간과 관계없이 다시 받아요.
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
    staleTime: DOCUMENT_STALE_TIME_MS,
  });
};

export default useDocumentQuery;
