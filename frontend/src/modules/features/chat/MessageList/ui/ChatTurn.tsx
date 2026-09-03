import styled from "@emotion/styled";
import ChatBubble from "@/shared/components/primitives/ui/ChatBubble";
import SourceButton from "@/shared/components/primitives/ui/SourceButton";
import type { ChatTurnStatus } from "../types/chatTurn";

import AnswerSkeleton from "./AnswerSkeleton";

/** 답변이 오다가 끊겼을 때 부분 답변 아래에 남기는 문구 */
const FAILURE_MESSAGE = "답변을 만들지 못했어요. 잠시 후 다시 시도해 주세요.";

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
 * 답변이 한 글자도 오지 않은 동안(`pending`)에는 들어올 문단 자리를 뼈대로 잡아 둡니다.
 *
 * 답변이 끊겨도(`error`) 여기까지 온 부분 답변은 지우지 않고 그 아래에 실패를 알립니다.
 * 읽던 내용을 되돌리면 무엇을 받았는지조차 알 수 없기 때문입니다.
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
  const isFailed = status === "error";
  const isWaiting = status === "pending";
  const hasSourceButton = isAnswered && Boolean(sourceLabel);
  const hasMeta = !isAnswered && Boolean(sourceLabel);

  return (
    <Container>
      <UserRow>
        <ChatBubble>{question}</ChatBubble>
      </UserRow>

      <Answer aria-live="polite">
        {hasMeta && <Meta>{sourceLabel}</Meta>}
        {isWaiting && <AnswerSkeleton />}
        {answer !== null && <AnswerText>{answer}</AnswerText>}
        {isFailed && <FailureText role="alert">{FAILURE_MESSAGE}</FailureText>}
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
  color: ${({ theme }) => theme.neutral[600]};
`;

const FailureText = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.sub.warning[600]};
`;

const AnswerText = styled.p`
  width: 100%;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[900]};
`;
