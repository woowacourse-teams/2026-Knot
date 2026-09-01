import useCreateChatSessionMutation from "@api/mutations/useCreateChatSessionMutation";
import useNavigateToChatSession from "@hooks/domain/chat/useNavigateToChatSession";
import { ChangeEvent, KeyboardEvent, SubmitEvent, useState } from "react";
import { useParams } from "react-router";

/**
 * 질문 입력과 제출을 다룹니다.
 *
 * 세션 없이 들어온 새 대화에서는 첫 질문을 보낼 때 세션을 만들고 그 대화로 옮겨 갑니다.
 * 세션이 생기기 전에는 메시지를 보낼 곳이 없기 때문입니다.
 */
export const useChatField = () => {
  const { workspaceId, sessionId } = useParams();
  const { navigateToChatSession } = useNavigateToChatSession();

  const { mutate: createChatSession, isPending } = useCreateChatSessionMutation(
    { workspaceId: Number(workspaceId) },
  );

  const [message, setMessage] = useState("");

  const canSubmit = message.trim().length > 0 && !isPending;

  const handleChange = (e: ChangeEvent<HTMLTextAreaElement>) =>
    setMessage(e.target.value);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key !== "Enter" || e.shiftKey) return;
    if (e.nativeEvent.isComposing) return; // 글자 조립 중일 때 폼 제출 막기

    e.preventDefault(); // "Enter" 입력 시 textarea 기본 동작(줄바꿈) 막기
    e.currentTarget.form?.requestSubmit(); // "Enter" 입력 시 폼 제출
  };

  const handleSubmit = (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!canSubmit || !workspaceId) return;

    // TODO: 메시지 전송 mutation 연결 (SSE)
    setMessage("");

    if (sessionId) return;

    createChatSession(
      {},
      {
        onSuccess: ({ id }) =>
          navigateToChatSession({ workspaceId, sessionId: String(id) }),
      },
    );
  };

  return {
    message,
    canSubmit,
    handleChange,
    handleKeyDown,
    handleSubmit,
  };
};
