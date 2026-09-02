import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router";
import useCreateChatSessionMutation from "@api/mutations/useCreateChatSessionMutation";
import useSendChatMessageMutation, {
  getChatStreamErrorMessage,
} from "@api/mutations/useSendChatMessageMutation";
import useNavigateToChatSession from "@hooks/domain/chat/useNavigateToChatSession";

/** 서버가 문구를 주지 못했을 때 보여 줄 안내 */
const SEND_ERROR_MESSAGE = "질문을 보내지 못했어요. 잠시 후 다시 시도해 주세요.";

interface StartStreamParams {
  /** 답변을 받을 대화 세션 ID */
  streamSessionId: number;
  /** 보낸 질문 */
  content: string;
}

/**
 * 질문 전송부터 답변이 서버 저장본으로 바뀌기까지의 상태를 들고 있습니다.
 *
 * 이 상태를 채팅 패널이 드는 이유는 두 가지입니다. 첫 질문으로 세션이 생기면 주소가
 * `/chat`에서 `/chat/:sessionId`로 바뀌는데, 두 주소가 같은 자리에 같은 패널을 그리므로
 * 여기의 상태는 이동을 견딥니다. 또 대화 목록을 펼쳤다 닫아도 패널은 그대로 있어
 * 답변이 도착하는 동안 목록을 구경해도 답변이 끊기지 않습니다.
 *
 * 조각(`delta`)은 뮤테이션이 아니라 여기에 쌓습니다. 
 * 뮤테이션은 보내는 중인지, 실패했는지, 끝났는지만 알고, 화면에 그릴 부분 답변은 이 state가 정본입니다.
 */
export const useChatStream = () => {
  const { workspaceId, sessionId } = useParams();
  const { navigateToChatSession } = useNavigateToChatSession();

  /** 아직 서버에 저장되지 않은, 진행 중인 턴의 질문. 없으면 null */
  const [question, setQuestion] = useState<string | null>(null);
  /** 도착한 조각을 이어 붙인 부분 답변 */
  const [answer, setAnswer] = useState("");
  /** 답변이 오다가 끊겼는지 여부 */
  const [isFailed, setIsFailed] = useState(false);
  /** 화면에 보여 줄 안내. 보낼 수 없었던 경우라 답변 자리에는 아무것도 남지 않습니다 */
  const [notice, setNotice] = useState<string | null>(null);

  /** 지금 흐르고 있는 스트림을 끊는 손잡이 */
  const abortControllerRef = useRef<AbortController | null>(null);
  /** 어느 세션의 답변을 받는 중인지. 주소가 그 세션을 떠났는지 판단하는 데 씁니다 */
  const streamingSessionIdRef = useRef<number | null>(null);
  /** 조각이 한 번이라도 도착했는지. 실패를 화면 어디에 알릴지 가릅니다 */
  const hasChunkRef = useRef(false);

  const { mutate: createChatSession, isPending: isCreatingSession } =
    useCreateChatSessionMutation({ workspaceId: Number(workspaceId) });
  const { mutate: sendChatMessage, isPending: isStreaming } =
    useSendChatMessageMutation({ workspaceId: Number(workspaceId) });

  // 세션을 만드는 동안에도 보내는 중입니다. 이때 한 번 더 보내면 세션이 둘로 갈라져요
  const isSending = isCreatingSession || isStreaming;

  const abortStream = () => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    streamingSessionIdRef.current = null;
  };

  const clearTurn = () => {
    setQuestion(null);
    setAnswer("");
    setIsFailed(false);
  };

  /**
   * 답변을 받지 못하고 끝났을 때의 뒷정리.
   *
   * 조각이 하나라도 왔다면 그 부분 답변을 남기고 턴에 실패를 표시합니다.
   * 한 글자도 못 받았다면 답변 자리에 남길 것이 없으므로 턴을 지우고 안내만 남깁니다.
   */
  const handleStreamFailure = (error: unknown) => {
    if (hasChunkRef.current) {
      setIsFailed(true);
      return;
    }

    clearTurn();
    setNotice(getChatStreamErrorMessage(error) ?? SEND_ERROR_MESSAGE);
  };

  const startStream = ({ streamSessionId, content }: StartStreamParams) => {
    const controller = new AbortController();

    abortControllerRef.current = controller;
    streamingSessionIdRef.current = streamSessionId;
    hasChunkRef.current = false;

    // 이미 다음 질문이 시작됐다면 그쪽 손잡이를 빼앗지 않도록 내 것일 때만 치웁니다
    const finishStream = () => {
      if (abortControllerRef.current !== controller) return;

      abortControllerRef.current = null;
      streamingSessionIdRef.current = null;
    };

    sendChatMessage(
      {
        sessionId: streamSessionId,
        content,
        signal: controller.signal,
        onChunk: (delta) => {
          hasChunkRef.current = true;
          setAnswer((prev) => prev + delta);
        },
      },
      {
        onSuccess: (complete) => {
          finishStream();

          // complete 없이 닫힌 스트림. 우리가 끊은 게 아니라면 도중에 끊긴 것입니다
          if (complete === null) {
            if (controller.signal.aborted) return;

            handleStreamFailure(null);
            return;
          }

          // 저장본을 이미 받아 둔 뒤라, 여기서 비워야 같은 답변이 두 번 보이지 않습니다
          clearTurn();
        },
        onError: (error) => {
          finishStream();
          handleStreamFailure(error);
        },
      },
    );
  };

  /**
   * 질문을 보냅니다.
   *
   * 세션이 없는 새 대화라면 세션을 먼저 만들고 그 대화로 옮긴 뒤에 보냅니다.
   * 세션이 생기기 전에는 메시지를 보낼 곳이 없기 때문입니다.
   */
  const handleSubmitQuestion = (content: string) => {
    if (!workspaceId || isSending) return;

    abortStream();
    setQuestion(content);
    setAnswer("");
    setIsFailed(false);
    setNotice(null);

    if (sessionId) {
      startStream({ streamSessionId: Number(sessionId), content });
      return;
    }

    createChatSession(
      {},
      {
        onSuccess: ({ id }) => {
          navigateToChatSession({
            workspaceId,
            sessionId: String(id),
            replace: true,
          });
          startStream({ streamSessionId: id, content });
        },
        onError: () => {
          clearTurn();
          setNotice(SEND_ERROR_MESSAGE);
        },
      },
    );
  };

  const openedSessionId = sessionId ? Number(sessionId) : null;

  // 다른 대화로 옮기면 진행 중이던 답변은 버립니다. 이어 보여주려면 세션마다 따로 들어야 해요
  useEffect(() => {
    const streamingSessionId = streamingSessionIdRef.current;
    if (streamingSessionId === null) return;
    if (streamingSessionId === openedSessionId) return;

    abortStream();
    clearTurn();
    setNotice(null);
  }, [openedSessionId]);

  // 화면을 떠날 때 열어 둔 연결을 닫습니다
  useEffect(() => () => abortStream(), []);

  return {
    streamingQuestion: question,
    streamedAnswer: answer,
    isStreamFailed: isFailed,
    isSending,
    notice,
    handleSubmitQuestion,
  };
};
