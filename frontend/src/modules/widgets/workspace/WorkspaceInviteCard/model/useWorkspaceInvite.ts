import useTimeout from "@hooks/common/useTimeout";
import useCopyWorkspaceInvite from "@hooks/domain/workspace/useCopyWorkspaceInvite";
import useNavigateToWorkspaceNotionConnection from "@hooks/domain/workspace/useNavigateToWorkspaceNotionConnection";
import useWorkspaceAccessGuard from "@hooks/domain/workspace/useWorkspaceAccessGuard";
import { useParams } from "react-router";

const TIMEOUT_DURATION = 2000;

/**
 * 팀원 초대 카드의 초대 조회·복사·다음 이동 흐름.
 *
 * 현재 `:workspaceId`의 활성 초대를 조회해 코드·링크를 얻고, 응답 전에는 `isLoading`으로 복사를 막아요.
 * 조회가 401·403·404로 실패하면 `useWorkspaceAccessGuard`가 로그인·워크스페이스 선택 화면으로 보내요.
 */
export const useWorkspaceInvite = () => {
  const { workspaceId } = useParams();
  const {
    inviteCode,
    displayInviteLink,
    isLoading,
    error,
    copyInviteCode,
    copyInviteLink,
  } = useCopyWorkspaceInvite({ workspaceId: Number(workspaceId) });

  useWorkspaceAccessGuard({ error });

  const { navigateToWorkspaceNotionConnection } =
    useNavigateToWorkspaceNotionConnection();
  const { start: startCodeTimeout, isTimedOut: isCodeCopied } = useTimeout({
    timeout: TIMEOUT_DURATION,
  });
  const { start: startLinkTimeout, isTimedOut: isLinkCopied } = useTimeout({
    timeout: TIMEOUT_DURATION,
  });

  const handleCopyCode = () => {
    copyInviteCode({
      onCopySuccess: () => {
        alert("참여 코드가 클립보드에 복사되었습니다.");
        startCodeTimeout();
      },
    });
  };

  const handleCopyLink = () => {
    copyInviteLink({
      onCopySuccess: () => {
        alert("초대 링크가 클립보드에 복사되었습니다.");
        startLinkTimeout();
      },
    });
  };

  const handleNext = () => {
    if (!workspaceId) return;

    navigateToWorkspaceNotionConnection(workspaceId);
  };

  return {
    inviteCode,
    displayInviteLink,
    isLoading,
    isCodeCopied,
    isLinkCopied,
    handleCopyLink,
    handleNext,
    handleCopyCode,
  };
};
