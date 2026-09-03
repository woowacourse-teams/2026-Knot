import styled from "@emotion/styled";
import type { ChatSession } from "../types/chatSession";
import { formatRelativeTime } from "../utils/formatRelativeTime";
import ChatSessionRow from "./ChatSessionRow";

interface ChatSessionGroupProps {
  label: string;
  sessions: ChatSession[];
  openedSessionId: string | null;
  onSelectSession: (sessionId: number) => void;
}

/**
 * "오늘"처럼 같은 기간에 묶인 대화를 기간 이름과 함께 보여줍니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1533
 */
export default function ChatSessionGroup({
  label,
  sessions,
  openedSessionId,
  onSelectSession,
}: ChatSessionGroupProps) {
  return (
    <Container>
      <Label>{label}</Label>

      <Rows>
        {sessions.map(({ id, title, lastMessageAt }) => (
          <ChatSessionRow
            key={id}
            title={title}
            lastMessageAt={formatRelativeTime({ date: lastMessageAt })}
            isSelected={openedSessionId === String(id)}
            onSelect={() => onSelectSession(id)}
          />
        ))}
      </Rows>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
`;

const Label = styled.h3`
  width: 100%;
  ${({ theme }) => theme.text.caption01};
  color: ${({ theme }) => theme.neutral[600]};
`;

const Rows = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
  width: 100%;
`;
