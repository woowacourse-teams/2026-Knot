import { ComponentProps } from "react";
import styled from "@emotion/styled";
import Textarea from "@/shared/components/primitives/ui/Textarea";
import ChatFieldSubmitButton from "./ui/ChatFieldSubmitButton";
import { useChatField } from "./model/useChatField";

interface ChatFieldProps
  extends Omit<ComponentProps<"textarea">, "value" | "onChange" | "onSubmit"> {
  /** 질문을 보내는 중인지 여부. 입력과 전송 버튼을 함께 잠급니다 */
  isSending: boolean;
  onSubmit: (message: string) => void;
}

/**
 * 질문을 적어 보내는 입력창.
 *
 * 보내는 동안에는 입력과 전송 버튼을 잠급니다. 답변이 오는 중에 또 보내면 서버가
 * 409(CHAT_STREAM_ALREADY_ACTIVE)로 거절하므로, 그 상황을 애초에 만들지 않습니다.
 *
 * 잠글 때 `disabled` 대신 `readOnly`를 쓰는 이유는 커서를 지키기 위해서입니다. `disabled`는
 * 포커스를 떨어뜨려서, 답변을 기다리는 30초 동안 입력창을 떠나 있게 됩니다.
 */
export default function ChatField({
  isSending,
  onSubmit,
  ...props
}: ChatFieldProps) {
  const {
    textareaRef,
    message,
    canSubmit,
    handleChange,
    handleKeyDown,
    handleSubmit,
  } = useChatField({ isSending, onSubmit });

  const idleStatus = canSubmit ? "active" : "inactive";

  return (
    <Container onSubmit={handleSubmit}>
      <ChatTextarea
        ref={textareaRef}
        rows={1}
        placeholder="무엇이든 요청하세요"
        value={message}
        readOnly={isSending}
        aria-disabled={isSending}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        {...props}
      />
      <ChatFieldSubmitButton status={isSending ? "loading" : idleStatus} />
    </Container>
  );
}

const Container = styled.form`
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  width: 100%;
  padding: 0.4375rem 0.5rem 0.4375rem 1.25rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 1.625rem;
  background-color: rgba(255, 255, 255, 0.08);
`;

const ChatTextarea = styled(Textarea)`
  flex: 1;
  min-width: 0;
  padding: 0.375rem 0;
  max-height: 12rem;
  overflow-y: auto;
  field-sizing: content;
  background-color: transparent;
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[0]};

  &::placeholder {
    color: ${({ theme }) => theme.neutral[500]};
  }
`;
