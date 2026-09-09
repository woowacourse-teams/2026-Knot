import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  PostChatMessageRequestDto,
  type ChatStreamErrorRaw,
  type PostChatMessageRequestInput,
} from "@api/dto/chatMessage";
import { streamDesktopAnswer } from "@api/desktopLlm";
import {
  ChatStreamRequestError,
  streamChatMessageApi,
} from "@api/fetch/api/v1/conversations/[sessionId]/messages";
import { chatKeys } from "@api/queryKey/chat";

/**
 * 연결이 열린 뒤 `error` 이벤트로 도착한 실패.
 *
 * 요청 함수는 이 이벤트를 조각과 같은 줄에 흘려보내지만, 화면에서는 답변이 끊긴 실패이므로
 * 여기서 throw로 바꿔 뮤테이션의 실패 상태에 싣습니다.
 */
export class ChatStreamEventError extends Error {
  /** 오류 코드. LLM_STREAM_FAILED 또는 LLM_STREAM_TIMEOUT */
  code: string;

  constructor({ code, message }: ChatStreamErrorRaw) {
    super(message);

    this.name = "ChatStreamEventError";
    this.code = code;
  }
}

/**
 * @description 사용자에게 그대로 보여 줄 수 있는 오류 문구를 꺼냅니다
 * @param error - 뮤테이션이 실패로 담은 값
 * @returns 서버가 내려준 문구. 서버가 준 오류가 아니면 null
 * @example
 * setNotice(getChatStreamErrorMessage(error) ?? SEND_ERROR_MESSAGE);
 */
export const getChatStreamErrorMessage = (error: unknown) =>
  error instanceof ChatStreamRequestError ||
  error instanceof ChatStreamEventError
    ? error.message
    : null;

/** 답변을 만든 경로. 데스크톱에서 사용자 구독으로 답했으면 `subscription`, 서버 모델이면 `server-sse` */
export type ChatAnsweredBy = "subscription" | "server-sse";

/** 답변이 끝나고 저장까지 마친 결과 */
export interface SendChatMessageComplete {
  /** 저장된 assistant 메시지 ID */
  messageId: number;
  answeredBy: ChatAnsweredBy;
}

interface SendChatMessageInput extends PostChatMessageRequestInput {
  /** 메시지를 보낼 대화 세션 ID */
  sessionId: number;
  /** 조각이 도착할 때마다 불립니다. 누적은 부르는 쪽이 합니다 */
  onChunk: (delta: string) => void;
  /** 화면을 떠나거나 다른 대화로 옮길 때 스트림을 끊는 신호 */
  signal?: AbortSignal;
}

interface UseSendChatMessageMutationParams {
  workspaceId: number;
}

/**
 * 대화 세션에 질문을 보내고 SSE로 도착하는 답변의 수명주기(대기·실패·완료)를 다룹니다.
 *
 * 도착 중인 조각은 화면이 계속 다시 그려져야 하므로 뮤테이션 상태가 아니라 `onChunk`로 넘겨
 * 부르는 쪽의 상태에 쌓습니다. 뮤테이션은 "보내는 중인가, 실패했는가, 끝났는가"만 압니다.
 *
 * 데스크톱 셸(`window.knotDesktop.llm`)에서는 먼저 사용자 본인의 Claude 구독으로 답을 받습니다
 * (기획서 6.5). 구독 미로그인·401·크레딧 소진처럼 첫 조각 전에 실패하면(`fallback: true`) 같은
 * 질문을 서버 SSE 경로로 다시 보내요. 브라우저에서는 처음부터 서버 SSE 경로입니다(로드맵 Q22).
 * 화면이 받는 조각·완료·실패 모양은 두 경로가 같습니다.
 *
 * 완료되면 화면의 정답은 서버 저장본이므로 메시지 이력과 세션 목록을 무효화합니다.
 * 무효화를 기다린 뒤에야 성공이 끝나므로, 부르는 쪽은 그때 쌓아 둔 조각을 비우면 됩니다.
 */
const useSendChatMessageMutation = ({
  workspaceId,
}: UseSendChatMessageMutationParams) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      sessionId,
      content,
      onChunk,
      signal,
    }: SendChatMessageInput): Promise<SendChatMessageComplete | null> => {
      const body = new PostChatMessageRequestDto({ content });
      const desktopLlm = window.knotDesktop?.llm;

      if (desktopLlm) {
        const outcome = await streamDesktopAnswer({
          llm: desktopLlm,
          workspaceId,
          sessionId,
          content: body.content,
          onChunk,
          signal,
        });

        if (outcome.kind === "cancelled") return null;
        if (outcome.kind === "complete") {
          return { messageId: outcome.messageId, answeredBy: "subscription" };
        }
        if (!outcome.fallback) throw new ChatStreamEventError(outcome);
        // 첫 조각 전의 구독 쪽 실패라 화면에 남은 조각이 없어요. 같은 질문을 서버 모델로 다시 보냅니다
      }

      for await (const { event, data } of streamChatMessageApi({
        sessionId,
        body,
        signal,
      })) {
        if (event === "chunk") {
          onChunk(data.delta);
          continue;
        }

        if (event === "error") throw new ChatStreamEventError(data);

        return { messageId: data.messageId, answeredBy: "server-sse" };
      }

      // complete 없이 끝난 스트림. 취소했을 때라 저장본도 없습니다
      return null;
    },

    onSuccess: async (complete, { sessionId }) => {
      if (complete === null) return;

      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: chatKeys.messages(sessionId),
        }),
        queryClient.invalidateQueries({
          queryKey: chatKeys.sessions(workspaceId),
        }),
      ]);
    },
  });
};

export default useSendChatMessageMutation;
