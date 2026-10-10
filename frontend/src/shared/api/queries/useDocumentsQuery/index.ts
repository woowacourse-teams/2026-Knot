import { getAllDocumentsApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents";
import { documentKeys } from "@api/queryKey/document";
import { useQuery } from "@tanstack/react-query";

interface UseDocumentsQueryParams {
  workspaceId: number;
  /** 이 녹음에서 나온 문서만 조회해요. 없으면 워크스페이스의 문서 전체를 조회해요 */
  recordingSessionId?: number;
}

/**
 * 워크스페이스의 문서와 주제 폴더를 조회하는 쿼리 훅. 녹음 ID를 주면 그 녹음에서 나온 문서만 조회해요.
 *
 * 한 페이지가 아니라 다음 페이지가 없을 때까지 이어 받아요. 문서를 폴더별로 묶거나 같은 녹음의 문서 사이를 옮겨 다니려면 전체가 있어야 해서예요.
 * 이어 받는 도중 한 요청이라도 실패하면 조회 전체가 실패예요.
 *
 * id가 정수인지는 확인하지 않고 그대로 요청해요. 잘못된 id인지는 서버가 판단해 400으로 답해요.
 */
const useDocumentsQuery = ({
  workspaceId,
  recordingSessionId,
}: UseDocumentsQueryParams) => {
  return useQuery({
    queryKey: documentKeys.list({ workspaceId, recordingSessionId }),
    queryFn: () => getAllDocumentsApi({ workspaceId, recordingSessionId }),
  });
};

export default useDocumentsQuery;
