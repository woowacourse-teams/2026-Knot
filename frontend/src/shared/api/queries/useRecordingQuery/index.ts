import { getRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]";
import { isHttpError } from "@api/httpClient/error";
import { recordingKeys } from "@api/queryKey/recording";
import { useQuery } from "@tanstack/react-query";

/** 문서 정리가 끝나기 전의 녹음 상태를 다시 물어보는 간격(ms) */
const POLL_INTERVAL_MS = 3000;

interface UseRecordingQueryParams {
  workspaceId: number;
  recordingId: number;
}

/**
 * 녹음 하나의 상태를 조회하는 쿼리 훅.
 *
 * 녹음을 끝낸 뒤 문서 정리가 진행 중인 동안(`ENDED` · `PROCESSING`)에는 3초 간격으로 다시 물어봐요.
 * 결과가 정해졌거나(`COMPLETED` · `NO_CONTENT` · `FAILED`) 아직 녹음 중이면(`RECORDING` · `PAUSED`) 멈춰요.
 *
 * 조회가 실패했을 때는 오류 종류로 나눠요.
 * 로그인 만료 · 권한 없음 · 없는 녹음처럼 다시 물어도 결과가 같은 오류(4xx)는 멈추고,
 * 네트워크 끊김이나 서버 오류는 잠깐의 문제일 수 있어 같은 간격으로 계속 물어봐요.
 *
 * 두 id는 정수여야 해요. 주소에서 읽은 값이 정수인지는 주소를 읽는 쪽(정리 화면 위젯)이 확인하고, 여기서는 다시 검사하지 않아요.
 */
const useRecordingQuery = ({
  workspaceId,
  recordingId,
}: UseRecordingQueryParams) => {
  return useQuery({
    queryKey: recordingKeys.detail({ workspaceId, recordingId }),
    queryFn: () => getRecordingApi({ workspaceId, recordingId }),
    refetchInterval: (query) => {
      const { data, error } = query.state;

      if (isHttpError(error) && error.isClientError) return false;

      // 아직 받지 못했으면(첫 조회가 일시 오류로 실패) 계속 물어봐요
      if (data === undefined) return POLL_INTERVAL_MS;

      const isOrganizing =
        data.status === "ENDED" || data.status === "PROCESSING";

      return isOrganizing ? POLL_INTERVAL_MS : false;
    },
  });
};

export default useRecordingQuery;
