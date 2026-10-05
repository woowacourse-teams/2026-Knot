import styled from "@emotion/styled";
import useConversationId from "@hooks/domain/search/useConversationId";
import useOpenedEvidenceMessage from "@hooks/domain/search/useOpenedEvidenceMessage";
import useOpenedSearchEvidences from "@hooks/domain/search/useOpenedSearchEvidences";
import useSearchMessages from "@hooks/domain/search/useSearchMessages";

import FoundRecordsIcon from "@/assets/icons/foundRecords.svg";

import { findLatestAnswerWithEvidence } from "./utils/findLatestAnswerWithEvidence";

/**
 * GNB 오른쪽에서 찾은 기록 패널을 여닫는 버튼.
 *
 * 근거가 있는 답변이 하나라도 있을 때만 보여요.
 * 닫혀 있을 때 누르면 근거가 있는 가장 최근 답변의 찾은 기록을 열고, 열려 있으면 닫아요.
 * 왼쪽 패널 버튼과 대칭이 되도록 같은 모양을 쓰고, 열려 있으면 채워진(Active) 모양이에요.
 */
export default function SearchEvidenceToggle() {
  const { conversationId } = useConversationId();
  const { messages } = useSearchMessages({ conversationId });
  const { evidences: openedEvidences } = useOpenedSearchEvidences({
    conversationId,
  });
  const { openEvidenceMessage, closeEvidenceMessage } =
    useOpenedEvidenceMessage();

  const latestAnswerWithEvidence = findLatestAnswerWithEvidence(messages);

  if (!latestAnswerWithEvidence) return null;

  // 근거 없는 답변 id가 주소에 있으면 패널은 없는데 버튼만 열림으로 보이는 어긋남을 막아요
  const isOpen = openedEvidences.length > 0;

  const handleClick = () => {
    if (isOpen) {
      closeEvidenceMessage();
      return;
    }

    openEvidenceMessage(latestAnswerWithEvidence.id);
  };

  return (
    <Trigger
      type="button"
      aria-label={isOpen ? "찾은 기록 닫기" : "찾은 기록 보기"}
      aria-expanded={isOpen}
      $isOpen={isOpen}
      onClick={handleClick}
    >
      <FoundRecordsIcon size={18} />
    </Trigger>
  );
}

// 왼쪽 패널 버튼(DockablePanel 트리거)과 같은 모양이에요. 둘 중 하나를 바꾸면 함께 바꿔요
const Trigger = styled.button<{ $isOpen: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5625rem; /* 9px */
  border: 1px solid
    ${({ theme, $isOpen }) =>
      $isOpen ? theme.neutral[700] : theme.neutral[200]};
  border-radius: 62.4375rem; /* 999px */
  background-color: ${({ theme, $isOpen }) =>
    $isOpen ? theme.neutral[700] : theme.neutral[0]};
  color: ${({ theme, $isOpen }) =>
    $isOpen ? theme.neutral[0] : theme.neutral[600]};
  box-shadow: ${({ theme }) => theme.shadow02};
  transition:
    background-color 0.2s ease-in,
    color 0.2s ease-in;

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;
