import { desktopLlmKeys } from "@api/queryKey/desktopLlm";
import { skipToken, useQuery } from "@tanstack/react-query";

import type { KnotDesktopApi } from "@/shared/types/desktop";

interface UseDesktopLlmStatusQueryParams {
  /** 데스크톱 셸의 preload `llm` API. 브라우저에서는 undefined라 요청을 보내지 않아요 */
  llm: KnotDesktopApi["llm"];
}

/**
 * 내 Claude 구독의 로그인 상태를 셸에서 읽습니다.
 *
 * 값의 출처가 서버 API가 아니라 셸의 preload(`window.knotDesktop.llm.getStatus`)라
 * `fetch/`의 요청 함수 없이 셸을 직접 부르는 쿼리예요. 폴링하지 않고, 상태가 바뀌면
 * 셸이 `llm.onStatusChanged`로 알려 주므로 쓰는 쪽이 그 값으로 캐시를 덮어요(로드맵 Q67).
 * 상태 객체에 토큰은 없어요.
 */
const useDesktopLlmStatusQuery = ({ llm }: UseDesktopLlmStatusQueryParams) => {
  return useQuery({
    queryKey: desktopLlmKeys.status(),
    queryFn: llm === undefined ? skipToken : () => llm.getStatus(),
  });
};

export default useDesktopLlmStatusQuery;
