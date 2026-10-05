import styled from "@emotion/styled";
import useOpenedEvidenceMessage from "@hooks/domain/search/useOpenedEvidenceMessage";
import ChatBubble from "@primitives/ui/ChatBubble";
import EvidenceButton from "@primitives/ui/EvidenceButton";

import type { SearchTurn as SearchTurnData } from "../types/searchTurn";

interface SearchTurnProps {
  turn: SearchTurnData;
}

/**
 * 질문 하나와 그에 대한 답변 하나를 묶어 보여주는 대화 단위.
 *
 * 질문은 오른쪽 말풍선, 답변은 왼쪽 평문 문단으로 그려요.
 * 답변에 근거가 있으면 그 아래 「기록 N개에서 찾았어요」 버튼으로 찾은 기록을 열어요.
 */
export default function SearchTurn({ turn }: SearchTurnProps) {
  const { question, answer } = turn;
  const { openedMessageId, openEvidenceMessage } = useOpenedEvidenceMessage();

  // TODO: BE가 답변 content를 마크다운으로 줘요
  // 마크다운 렌더러로 바꾸면 줄 단위 split과 Paragraph는 빠져요.
  const paragraphs =
    answer?.content.split("\n").filter((line) => line.trim() !== "") ?? [];
  const evidenceCount = answer?.evidences.length ?? 0;

  return (
    <Container>
      <QuestionRow>
        <ChatBubble>{question.content}</ChatBubble>
      </QuestionRow>

      {answer && (
        <Answer>
          {paragraphs.map((paragraph, index) => (
            <Paragraph key={index}>{paragraph}</Paragraph>
          ))}

          {evidenceCount > 0 && (
            <EvidenceButtonWrapper>
              <EvidenceButton
                count={evidenceCount}
                isOpen={openedMessageId === answer.id}
                onClick={() => openEvidenceMessage(answer.id)}
              />
            </EvidenceButtonWrapper>
          )}
        </Answer>
      )}
    </Container>
  );
}

const Container = styled.article`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
`;

const QuestionRow = styled.div`
  display: flex;
  justify-content: flex-end;
`;

const Answer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
`;

const Paragraph = styled.p`
  overflow-wrap: anywhere;
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[900]};
`;

/** 버튼이 답변 폭만큼 늘어나지 않고 문구 길이에 맞도록 감싸요 */
const EvidenceButtonWrapper = styled.div`
  display: flex;
`;
