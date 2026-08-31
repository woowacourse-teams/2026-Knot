import styled from "@emotion/styled";
import ChatBubble from "@/shared/components/primitives/ui/ChatBubble";
import SourceButton from "@/shared/components/primitives/ui/SourceButton";
import type { ChatTurnStatus } from "../types/chatTurn";

interface ChatTurnProps {
  question: string;
  answer: string | null;
  status: ChatTurnStatus;
  sourceLabel?: string;
  isSourceOpen?: boolean;
  onOpenSource?: () => void;
}

/**
 * 질문 하나와 그에 대한 답변 하나를 묶어 보여주는 대화 단위.
 *
 * 질문은 우측 말풍선, 답변은 좌측 평문으로 그립니다.
 * 근거 문구(`sourceLabel`)는 답변이 끝나면 버튼으로, 그 전에는 답변 위 회색 줄로 나옵니다.
 * 같은 문구이므로 둘이 동시에 보이지는 않습니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1198-721
 */
export default function ChatTurn({
  question,
  answer,
  status,
  sourceLabel,
  isSourceOpen = false,
  onOpenSource,
}: ChatTurnProps) {
  const isAnswered = status === "done";
  const hasSourceButton = isAnswered && Boolean(sourceLabel);
  const hasMeta = !isAnswered && Boolean(sourceLabel);

  return (
    <Container>
      <UserRow>
        <ChatBubble>{question}</ChatBubble>
      </UserRow>

      <Answer aria-live="polite">
        {hasMeta && <Meta>{sourceLabel}</Meta>}
        {answer !== null && <AnswerText>{answer}</AnswerText>}
      </Answer>

      {hasSourceButton && (
        <SourceButton isSelected={isSourceOpen} onClick={onOpenSource}>
          {sourceLabel}
        </SourceButton>
      )}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
  width: 100%;
`;

const UserRow = styled.div`
  display: flex;
  justify-content: flex-end;
  width: 100%;
`;

const Answer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  width: 100%;
`;

const Meta = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;

const AnswerText = styled.p`
  width: 100%;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[0]};
`;
