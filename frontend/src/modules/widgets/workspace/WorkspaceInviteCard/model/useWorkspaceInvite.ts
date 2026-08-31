import useClipboard from "@hooks/common/useClipboard";
import useTimeout from "@hooks/common/useTimeout";
import useNavigateToWorkspaceNotionConnection from "@hooks/domain/workspace/useNavigateToWorkspaceNotionConnection";
import { useParams } from "react-router";

import { getInviteLink } from "../utils/getInviteLink";

const TIMEOUT_DURATION = 2000;

export const useWorkspaceInvite = () => {
  const { workspaceId } = useParams();
  const { copy } = useClipboard();

  const { navigateToWorkspaceNotionConnection } =
    useNavigateToWorkspaceNotionConnection();
  const { start: startCodeTimeout, isTimedOut: isCodeCopied } = useTimeout({
    timeout: TIMEOUT_DURATION,
  });
  const { start: startLinkTimeout, isTimedOut: isLinkCopied } = useTimeout({
    timeout: TIMEOUT_DURATION,
  });

  // TODO(#229): 초대 API 연결 후 응답의 참여 코드로 교체
  const inviteCode = "X35D3S";
  // TODO(#229): 초대 API 연결 후 응답의 linkToken으로 교체. 링크 진입 게이트의 임시 통과 토큰과 같은 값이에요
  const linkToken = "Xk3vQ9mZp2LrT7wB1nHc4A";
  const { displayInviteLink, inviteLink } = getInviteLink(linkToken);

  const handleCopyCode = () => {
    copy({
      text: inviteCode,
      onCopySuccess: () => {
        alert("참여 코드가 클립보드에 복사되었습니다.");
        startCodeTimeout();
      },
    });
  };

  const handleCopyLink = () => {
    copy({
      text: inviteLink,
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
