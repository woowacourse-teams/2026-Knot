import useClipboard from "@hooks/common/useClipboard";
import { getInviteLink } from "@utils/getInviteLink";

interface CopyInviteParams {
  /** 클립보드에 쓰는 데 성공했을 때 실행돼요. 실패하면 호출되지 않아요. */
  onCopySuccess?: () => void;
}

/**
 * 워크스페이스 참여 코드·초대 링크와 각각을 클립보드로 복사하는 함수를 주는 도메인 훅.
 *
 * 팀원 초대 화면 카드와 홈의 팀원 초대 카드가 같은 코드·링크·복사 로직을 쓰기 위한 훅이에요.
 * 복사 뒤의 피드백(`복사됨` 표시, alert 등)은 화면마다 다르므로 여기서 정하지 않고
 * `onCopySuccess`로 쓰는 쪽에 맡깁니다. 클립보드에 쓰지 못하면 조용히 넘어가요.
 */
const useCopyWorkspaceInvite = () => {
  const { copy } = useClipboard();

  // TODO(#229): 초대 API 연결 후 응답의 참여 코드로 교체
  const inviteCode = "X35D3S";
  // TODO(#229): 초대 API 연결 후 응답의 linkToken으로 교체. 링크 진입 게이트의 임시 통과 토큰과 같은 값이에요
  const linkToken = "Xk3vQ9mZp2LrT7wB1nHc4A";
  const { displayInviteLink, inviteLink } = getInviteLink(linkToken);

  const copyInviteCode = ({ onCopySuccess }: CopyInviteParams = {}) => {
    copy({ text: inviteCode, onCopySuccess });
  };

  const copyInviteLink = ({ onCopySuccess }: CopyInviteParams = {}) => {
    copy({ text: inviteLink, onCopySuccess });
  };

  return {
    inviteCode,
    inviteLink,
    displayInviteLink,
    copyInviteCode,
    copyInviteLink,
  };
};

export default useCopyWorkspaceInvite;
