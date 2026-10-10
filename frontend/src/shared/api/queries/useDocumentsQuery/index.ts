import { getAllDocumentsApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents";
import { documentKeys } from "@api/queryKey/document";
import { useQuery } from "@tanstack/react-query";

interface UseDocumentsQueryParams {
  workspaceId: number;
}

/**
 * 워크스페이스의 문서 전체와 주제 폴더를 조회하는 쿼리 훅.
 *
 * 문서 목록 화면이 써요. 문서를 폴더별로 묶으려면 전체가 있어야 해서, 한 페이지가 아니라 다음 페이지가 없을 때까지 이어 받아요.
 * 이어 받는 도중 한 요청이라도 실패하면 조회 전체가 실패예요.
 *
 * `workspaceId`는 정수여야 해요. 주소에서 읽은 값이 정수인지는 주소를 읽는 쪽(문서 목록 위젯)이 확인하고, 여기서는 다시 검사하지 않아요.
 */
const useDocumentsQuery = ({ workspaceId }: UseDocumentsQueryParams) => {
  return useQuery({
    queryKey: documentKeys.list({ workspaceId }),
    queryFn: () => getAllDocumentsApi({ workspaceId }),
  });
};

export default useDocumentsQuery;
