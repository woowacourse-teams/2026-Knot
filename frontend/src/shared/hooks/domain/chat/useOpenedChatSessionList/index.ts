import { useSearchParams } from "react-router";

/** 대화 목록을 펼쳐 두었는지 담는 쿼리 파라미터 이름. */
const CHAT_SESSION_LIST_PARAM = "chatSessionList";

/** 목록을 펼쳐 두었을 때 파라미터에 담기는 값. */
const OPENED_VALUE = "open";

/**
 * 지금 대화 목록을 펼쳐 두었는지 다루는 도메인 훅.
 *
 * 목록을 여는 곳(채팅 패널 헤더)과 닫는 곳(목록에서 대화 선택)이 서로 다른 컴포넌트라
 * 어느 한쪽의 state로 두면 다른 쪽이 볼 수 없습니다. 그래서 URL의 쿼리 파라미터를 값의 원본으로 씁니다.
 *
 * 대화를 옮기면 경로가 바뀌면서 파라미터도 함께 사라지므로, 목록에서 대화를 고르면 목록은 저절로 닫힙니다.
 */
const useOpenedChatSessionList = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const isChatSessionListOpen =
    searchParams.get(CHAT_SESSION_LIST_PARAM) === OPENED_VALUE;

  const openChatSessionList = () => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set(CHAT_SESSION_LIST_PARAM, OPENED_VALUE);

        return next;
      },
      { replace: true }, // 목록을 여닫은 자취를 히스토리에 남기지 않습니다
    );
  };

  const closeChatSessionList = () => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete(CHAT_SESSION_LIST_PARAM);

        return next;
      },
      { replace: true },
    );
  };

  return { isChatSessionListOpen, openChatSessionList, closeChatSessionList };
};

export default useOpenedChatSessionList;
