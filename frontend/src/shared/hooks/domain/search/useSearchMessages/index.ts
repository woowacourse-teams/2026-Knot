import type { SearchMessage } from "@/shared/types/search";

import { SEARCH_MESSAGES_MOCK } from "./mock";

interface UseSearchMessagesParams {
  /** 대화 ID. 없으면 아직 질문을 보내지 않은 새 대화예요. */
  conversationId?: number;
}

const EMPTY_MESSAGES: SearchMessage[] = [];

/**
 * 탐색 대화의 메시지 목록을 주는 도메인 훅.
 *
 * 대화 칸·찾은 기록 패널·GNB의 찾은 기록 버튼이 같은 메시지를 봐야 하므로 출처를 이 훅 하나로 모아요.
 * 대화 ID가 없으면 빈 목록을, 있으면 고정된 예시 대화를 줘요.
 */
// TODO: v2 메시지 조회 API가 나오면 쿼리로 교체
const useSearchMessages = ({ conversationId }: UseSearchMessagesParams) => {
  const messages =
    conversationId === undefined ? EMPTY_MESSAGES : SEARCH_MESSAGES_MOCK;

  return { messages };
};

export default useSearchMessages;
