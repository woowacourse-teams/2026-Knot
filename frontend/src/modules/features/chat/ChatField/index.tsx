import { ComponentProps } from "react";
import styled from "@emotion/styled";
import Textarea from "@/shared/components/primitives/ui/Textarea";
import ChatFieldSubmitButton from "./ui/ChatFieldSubmitButton";
import { useChatField } from "./model/useChatField";

interface ChatFieldProps extends Omit<
  ComponentProps<"textarea">,
  "value" | "onChange"
> {}

export default function ChatField({ ...props }: ChatFieldProps) {
  const { message, submitStatus, handleChange, handleKeyDown, handleSubmit } =
    useChatField();

  return (
    <Container onSubmit={handleSubmit}>
      <ChatTextarea
        rows={1}
        placeholder="무엇이든 요청하세요"
        value={message}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        {...props}
      />
      <ChatFieldSubmitButton status={submitStatus} />
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
