import useWorkspaceInvitationQuery from "@api/queries/useWorkspaceInvitationQuery";
import useClipboard from "@hooks/common/useClipboard";
import { getInviteLink } from "@utils/getInviteLink";

interface UseCopyWorkspaceInviteParams {
  workspaceId: number;
}

interface CopyInviteParams {
  /** 클립보드에 쓰는 데 성공했을 때 실행돼요. 실패하면 호출되지 않아요. */
  onCopySuccess?: () => void;
}

/**
 * 워크스페이스 참여 코드·초대 링크와 각각을 클립보드로 복사하는 함수를 주는 도메인 훅.
 *
 * 팀원 초대 화면 카드와 홈의 팀원 초대 카드가 같은 코드·링크·복사 로직을 쓰기 위한 훅이에요.
 * 코드와 링크 토큰은 활성 초대 조회(`useWorkspaceInvitationQuery`)의 응답에서 와요.
 * 아직 응답이 없으면 `inviteCode`·`inviteLink`·`displayInviteLink`는 `undefined`이고 복사 함수는 아무것도 하지 않아요.
 * 조회 실패는 여기서 판정하지 않고 `error`로 돌려주니, 이동이 필요하면 쓰는 쪽에서 `useWorkspaceAccessGuard`에 넘기세요.
 *
 * 복사 뒤의 피드백(`복사됨` 표시 등)은 화면마다 다르므로 여기서 정하지 않고
 * `onCopySuccess`로 쓰는 쪽에 맡깁니다. 클립보드에 쓰지 못하면 조용히 넘어가요.
 */
const useCopyWorkspaceInvite = ({
  workspaceId,
}: UseCopyWorkspaceInviteParams) => {
  const { copy } = useClipboard();
  const {
    data: invitation,
    isPending,
    error,
  } = useWorkspaceInvitationQuery({ workspaceId });

  const inviteCode = invitation?.code;
  const link = invitation && getInviteLink(invitation.linkToken);
  const inviteLink = link?.inviteLink;
  const displayInviteLink = link?.displayInviteLink;

  const copyInviteCode = ({ onCopySuccess }: CopyInviteParams = {}) => {
    if (inviteCode === undefined) return;

    copy({ text: inviteCode, onCopySuccess });
  };

  const copyInviteLink = ({ onCopySuccess }: CopyInviteParams = {}) => {
    if (inviteLink === undefined) return;

    copy({ text: inviteLink, onCopySuccess });
  };

  return {
    inviteCode,
    inviteLink,
    displayInviteLink,
    isLoading: isPending,
    error,
    copyInviteCode,
    copyInviteLink,
  };
};

export default useCopyWorkspaceInvite;
