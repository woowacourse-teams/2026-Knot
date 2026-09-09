import { desktopLlmKeys } from "@api/queryKey/desktopLlm";
import { skipToken, useQuery } from "@tanstack/react-query";

import type { KnotDesktopApi } from "@/shared/types/desktop";

interface UseDesktopLlmSettingsQueryParams {
  /** 데스크톱 셸의 preload `llm` API. 브라우저에서는 undefined라 요청을 보내지 않아요 */
  llm: KnotDesktopApi["llm"];
}

/**
 * 구독 호출의 모델·effort와 셸이 허용하는 목록을 읽습니다.
 *
 * 목록을 화면이 하드코딩하지 않고 셸(`window.knotDesktop.llm.getSettings`)에서 받아요(로드맵 Q62·Q67).
 * 저장(`updateSettings`)이 돌려주는 값으로 쓰는 쪽이 캐시를 덮으므로 다시 읽지 않아요.
 */
const useDesktopLlmSettingsQuery = ({
  llm,
}: UseDesktopLlmSettingsQueryParams) => {
  return useQuery({
    queryKey: desktopLlmKeys.settings(),
    queryFn: llm === undefined ? skipToken : () => llm.getSettings(),
  });
};

export default useDesktopLlmSettingsQuery;
