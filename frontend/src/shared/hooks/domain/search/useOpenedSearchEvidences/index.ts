import useOpenedEvidenceMessage from "@hooks/domain/search/useOpenedEvidenceMessage";
import useSearchMessages from "@hooks/domain/search/useSearchMessages";

interface UseOpenedSearchEvidencesParams {
  /** 대화 ID. 없으면 아직 질문을 보내지 않은 새 대화예요. */
  conversationId?: number;
}

/**
 * 지금 찾은 기록을 펼쳐 둔 답변의 근거 문서를 주는 도메인 훅.
 *
 * 찾은 기록 패널(`SearchEvidenceList`)은 이 목록을 그리고, GNB 찾은 기록 버튼(`SearchEvidenceToggle`)은
 * 목록으로 패널이 열려 있는지 판단해요. 두 곳이 같은 기준으로 "열린 근거"를 보도록 훅으로 내렸어요.
 * 펼친 답변이 없거나 근거가 없으면 빈 목록이에요.
 */
const useOpenedSearchEvidences = ({
  conversationId,
}: UseOpenedSearchEvidencesParams) => {
  const { messages } = useSearchMessages({ conversationId });
  const { openedMessageId } = useOpenedEvidenceMessage();

  const openedAnswer = messages.find(
    ({ id, role }) => role === "ASSISTANT" && id === openedMessageId,
  );
  // 서버가 관련도 순(rank 오름차순)으로 최대 3개까지 줘요.
  const evidences = openedAnswer?.evidences ?? [];

  return { evidences };
};

export default useOpenedSearchEvidences;
