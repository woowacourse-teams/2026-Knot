import { desktopAgentKeys } from "@api/queryKey/desktopAgent";
import { skipToken, useQuery } from "@tanstack/react-query";

import type { KnotDesktopApi } from "@/shared/types/desktop";

/** 마지막 도구 호출 시각을 다시 읽는 간격(ms). CLI가 실제로 붙었는지 화면에서 확인하기 위해서예요 */
const STATUS_REFETCH_INTERVAL_MS = 10_000;

interface UseDesktopAgentStatusQueryParams {
  /** 데스크톱 셸의 preload `agent` API. 브라우저에서는 undefined라 요청을 보내지 않아요 */
  agent: KnotDesktopApi["agent"];
}

/**
 * 데스크톱 셸이 띄운 로컬 MCP 서버의 상태를 읽습니다.
 *
 * 값의 출처가 서버 API가 아니라 셸의 preload(`window.knotDesktop.agent.getStatus`)라
 * `fetch/`의 요청 함수 없이 셸을 직접 부르는 유일한 쿼리예요. 폴링·재조회·캐시는
 * 다른 쿼리와 같은 방식으로 다루려고 여기 둡니다.
 *
 * 포트 충돌 같은 기동 실패는 거절이 아니라 상태의 `error`로 오므로, 쓰는 쪽은
 * 포트 변경·토큰 재발급 뒤 `refetch`로 새 상태를 읽어요. 10초마다 다시 읽어
 * 마지막 도구 호출 시각이 갱신되게 합니다.
 */
const useDesktopAgentStatusQuery = ({
  agent,
}: UseDesktopAgentStatusQueryParams) => {
  return useQuery({
    queryKey: desktopAgentKeys.status(),
    queryFn: agent === undefined ? skipToken : () => agent.getStatus(),
    refetchInterval: STATUS_REFETCH_INTERVAL_MS,
  });
};

export default useDesktopAgentStatusQuery;
