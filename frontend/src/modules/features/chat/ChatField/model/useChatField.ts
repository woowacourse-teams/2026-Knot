import { ChangeEvent, KeyboardEvent, SubmitEvent, useRef, useState } from "react";

interface UseChatFieldParams {
  /** 질문을 보내는 중인지 여부. 보내는 동안에는 다시 보낼 수 없습니다 */
  isSending: boolean;
  onSubmit: (message: string) => void;
}

/**
 * 질문 입력과 제출을 다룹니다.
 *
 * 보낸 뒤의 일(세션 생성·이동·스트리밍)은 이 입력창의 관심사가 아니라 채팅 패널이 맡으므로
 * 여기서는 무엇을 보냈는지만 알려주고 입력을 비웁니다.
 *
 * 보낸 뒤에도 커서는 입력창에 남습니다. 대화는 대개 한 번으로 끝나지 않는데, 보낼 때마다
 * 입력창을 다시 눌러야 하면 이어 묻기가 번거로워집니다.
 */
export const useChatField = ({ isSending, onSubmit }: UseChatFieldParams) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [message, setMessage] = useState("");

  const canSubmit = message.trim().length > 0 && !isSending;

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
    if (!canSubmit) return;

    onSubmit(message.trim());
    setMessage("");
    textareaRef.current?.focus();
  };

  return {
    textareaRef,
    message,
    canSubmit,
    handleChange,
    handleKeyDown,
    handleSubmit,
  };
};
