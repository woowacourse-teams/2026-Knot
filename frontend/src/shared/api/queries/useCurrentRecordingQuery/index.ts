import { getCurrentRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/current";
import { recordingKeys } from "@api/queryKey/recording";
import { useQuery } from "@tanstack/react-query";

interface UseCurrentRecordingQueryParams {
  workspaceId: number;
}

/**
 * 워크스페이스에서 내 화면에 표시할 현재 녹음 한 건을 조회하는 쿼리 훅.
 *
 * 홈의 「진행 중인 녹음」 카드가 녹음 상태와 누적 녹음 시간을 보여 주는 데 써요.
 * 표시할 녹음이 없으면(204) `data`가 `null`이에요.
 *
 * 라우트 파라미터를 `Number`로 바꾼 값이 정수가 아니면(`/workspace/abc` 같은 잘못된 주소) 요청하지 않아요.
 */
const useCurrentRecordingQuery = ({
  workspaceId,
}: UseCurrentRecordingQueryParams) => {
  return useQuery({
    queryKey: recordingKeys.current(workspaceId),
    queryFn: () => getCurrentRecordingApi(workspaceId),
  });
};

export default useCurrentRecordingQuery;
