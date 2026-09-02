import styled from "@emotion/styled";
import Textarea from "@primitives/ui/Textarea";

import GhostIcon from "@/assets/icons/ghost.svg";
import SendIcon from "@/assets/icons/send.svg";

import { useWorkspaceDock } from "./model/useWorkspaceDock";

/**
 * 화면 하단 가운데에 고정으로 놓이는 독.
 *
 * 접혀 있을 때는 동그란 버튼 하나이고, 누르면 질문 입력창으로 펼쳐져요.
 * 질문을 보내면 어느 화면에 있었든 탐색 화면으로 옮겨 가며 그 질문으로 대화를 시작해요.
 * Figma에서 숨겨져 있는 회의 녹음·글 작성 슬롯은 만들지 않아요.
 *
 * 화면 어디에 놓을지는 이 독을 쓰는 레이아웃이 정해요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1347-862 Dock/Bar}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1379-1834 Dock/Bar 모드=채팅 입력}
 */
export default function WorkspaceDock() {
  const {
    textareaRef,
    isExpanded,
    message,
    canSubmit,
    handleExpand,
    handleChange,
    handleKeyDown,
    handleSubmit,
  } = useWorkspaceDock();

  if (!isExpanded) {
    return (
      <CollapsedBar>
        <CollapsedButton type="button" aria-label="무엇이든 요청하기" onClick={handleExpand}>
          <GhostIcon size={24} />
        </CollapsedButton>
      </CollapsedBar>
    );
  }

  return (
    <ExpandedBar onSubmit={handleSubmit}>
      <GhostIcon size={24} />

      <MessageField
        ref={textareaRef}
        rows={1}
        aria-label="무엇이든 요청하세요"
        placeholder="무엇이든 요청하세요"
        value={message}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
      />

      <SubmitButton type="submit" aria-label="보내기" disabled={!canSubmit}>
        <SendIcon size={16} />
      </SubmitButton>
    </ExpandedBar>
  );
}

const barStyle = `
  display: flex;
  align-items: center;
  height: 3.75rem; /* 60px */
  border-radius: 6.25rem; /* 100px */
`;

const CollapsedBar = styled.div`
  ${barStyle}
  padding: 0 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[800]};
  box-shadow: ${({ theme }) => theme.shadow03};
`;

const CollapsedButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem; /* 40px */
  height: 2.5rem;
  border-radius: 1.25rem;
  color: ${({ theme }) => theme.neutral[0]};

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const ExpandedBar = styled.form`
  ${barStyle}
  gap: 0.625rem; /* 10px */
  width: min(50rem, 100%); /* 800px */
  padding: 0 0.75rem 0 1.25rem; /* 0 12px 0 20px */
  background-color: ${({ theme }) => theme.neutral[800]};
  box-shadow: ${({ theme }) => theme.shadow03};
  color: ${({ theme }) => theme.neutral[0]};

  & > svg {
    flex-shrink: 0;
  }
`;

const MessageField = styled(Textarea)`
  flex: 1;
  min-width: 0;
  max-height: 7.5rem;
  overflow-y: auto;
  field-sizing: content;
  background-color: transparent;
  color: ${({ theme }) => theme.neutral[0]};
  ${({ theme }) => theme.text.body01};

  &::placeholder {
    color: ${({ theme }) => theme.neutral[400]};
  }
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1080-648 Button/Send} */
const SubmitButton = styled.button`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 2.25rem; /* 36px */
  height: 2.25rem;
  border-radius: 62.4375rem;
  background-color: ${({ theme }) => theme.neutral[0]};
  color: ${({ theme }) => theme.neutral[800]};
  transition:
    background-color 0.2s ease-in,
    color 0.2s ease-in;

  &:disabled {
    background-color: ${({ theme }) => theme.neutral[500]};
    color: ${({ theme }) => theme.neutral[700]};
    cursor: not-allowed;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;
