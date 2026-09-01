import useTimeout from "@hooks/common/useTimeout";
import useCopyWorkspaceInvite from "@hooks/domain/workspace/useCopyWorkspaceInvite";

/** `복사됨` 표시를 유지하는 시간(ms). 팀원 초대 화면 카드와 같아요. */
const COPIED_DURATION_MS = 2000;

export const useWorkspaceInviteMemberCard = () => {
  const { displayInviteLink, copyInviteCode, copyInviteLink } =
    useCopyWorkspaceInvite();
  const { start: startLinkTimeout, isTimedOut: isLinkCopied } = useTimeout({
    timeout: COPIED_DURATION_MS,
  });
  const { start: startCodeTimeout, isTimedOut: isCodeCopied } = useTimeout({
    timeout: COPIED_DURATION_MS,
  });

  const handleCopyLink = () => {
    copyInviteLink({ onCopySuccess: startLinkTimeout });
  };

  const handleCopyCode = () => {
    copyInviteCode({ onCopySuccess: startCodeTimeout });
  };

  return {
    displayInviteLink,
    isLinkCopied,
    isCodeCopied,
    handleCopyLink,
    handleCopyCode,
  };
};
