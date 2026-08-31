import { ChangeEvent, KeyboardEvent, SubmitEvent, useState } from "react";

export const useChatField = () => {
  const [message, setMessage] = useState("");

  const canSubimt = message.trim().length > 0;

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
    if (message.trim().length === 0) return;

    // TODO: 메시지 전송 mutation 연결
  };

  return {
    message,
    canSubimt,
    handleChange,
    handleKeyDown,
    handleSubmit,
  };
};
