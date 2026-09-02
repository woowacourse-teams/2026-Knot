import { useSearchParams } from "react-router";

/** 어느 답변의 근거 문서를 펼쳤는지 담는 쿼리 파라미터 이름. */
const SOURCE_MESSAGE_PARAM = "messageId";

/**
 * 지금 근거 문서를 펼쳐 둔 답변이 무엇인지 다루는 도메인 훅.
 *
 * 근거 버튼은 대화 패널에, 문서 목록은 그 옆 레일에 있어 서로 다른 화면 구획에 놓입니다.
 * 두 곳이 같은 상태를 봐야 하므로 URL의 쿼리 파라미터를 값의 원본으로 씁니다.
 * 대화를 옮기면 경로가 바뀌면서 파라미터도 함께 사라집니다.
 * 닫으면 파라미터를 지웁니다. 그러면 문서 레일이 자리를 비워 대화가 화면을 넓게 씁니다.
 */
const useOpenedSourceMessage = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const parsedMessageId = Number(searchParams.get(SOURCE_MESSAGE_PARAM));
  const isValidMessageId =
    Number.isInteger(parsedMessageId) && parsedMessageId > 0;
  const openedMessageId = isValidMessageId ? parsedMessageId : null;

  const openSourceMessage = (messageId: number) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set(SOURCE_MESSAGE_PARAM, String(messageId));

        return next;
      },
      { replace: true }, 
    );
  };

  const closeSourceMessage = () => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete(SOURCE_MESSAGE_PARAM);

        return next;
      },
      { replace: true },
    );
  };

  return { openedMessageId, openSourceMessage, closeSourceMessage };
};

export default useOpenedSourceMessage;
