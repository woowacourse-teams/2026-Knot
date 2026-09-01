import useTimeout from "@hooks/common/useTimeout";
import useCopyWorkspaceInvite from "@hooks/domain/workspace/useCopyWorkspaceInvite";
import useNavigateToWorkspaceNotionConnection from "@hooks/domain/workspace/useNavigateToWorkspaceNotionConnection";
import { useParams } from "react-router";

const TIMEOUT_DURATION = 2000;

export const useWorkspaceInvite = () => {
  const { workspaceId } = useParams();
  const { inviteCode, displayInviteLink, copyInviteCode, copyInviteLink } =
    useCopyWorkspaceInvite();

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
    isCodeCopied,
    isLinkCopied,
    displayInviteLink,
    handleCopyLink,
    handleNext,
    handleCopyCode,
  };
};
