import useNavigateToNewChat from "@hooks/domain/chat/useNavigateToNewChat";
import { useEffect, useRef } from "react";
import { useLocation, useParams } from "react-router";

import type { ChatNavigationState } from "@/shared/types/chat";

interface UseChatPanelParams {
  /** 하단 독에서 들고 온 질문을 보낼 함수 */
  onSubmitPendingQuestion: (question: string) => void;
}

/**
 * 채팅 패널이 화면에 붙을 때 해야 할 일을 다룹니다.
 *
 * 하단 독에서 질문을 적어 보내면 이 화면으로 옮겨 오면서 질문이 히스토리 state에 실려 옵니다.
 * 그 질문은 도착 즉시 한 번만 보냅니다. 보내고 나면 세션이 생기며 주소가 바뀌어 state가 사라지지만,
 * 보내지 못하고 끝난 경우에는 state가 남으므로 이미 보냈는지를 따로 기억해 두 번 보내지 않습니다.
 */
export const useChatPanel = ({
  onSubmitPendingQuestion,
}: UseChatPanelParams) => {
  const { workspaceId } = useParams();
  const { state } = useLocation();
  const { navigateToNewChat } = useNavigateToNewChat();

  const pendingQuestion = (state as ChatNavigationState | null)?.question;
  const hasSubmittedPendingQuestionRef = useRef(false);

  useEffect(() => {
    if (!pendingQuestion) return;
    if (hasSubmittedPendingQuestionRef.current) return;

    hasSubmittedPendingQuestionRef.current = true;
    onSubmitPendingQuestion(pendingQuestion);
  }, [pendingQuestion, onSubmitPendingQuestion]);

  const handleStartNewChat = () => {
    if (!workspaceId) return;

    navigateToNewChat(workspaceId);
  };

  return { handleStartNewChat };
};
