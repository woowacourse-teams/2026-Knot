import { getRouterPath } from "@routes/PATH_ROUTE";

/**
 * linkToken으로 팀원에게 보낼 초대 링크를 만들어요.
 *
 * 링크를 열면 초대 링크 진입 화면(`/invite/:token`)이 토큰을 판정한 뒤
 * 입장 확인이나 초대 링크 오류 화면으로 보내요.
 * 링크에는 BE 발급 응답의 `linkToken`만 넣고 6자 참여 코드는 노출하지 않아요.
 */
export const getInviteLink = (linkToken: string) => {
  const displayInviteLink = getRouterPath({
    routeKey: "INVITE",
    params: { token: linkToken },
  });

  return {
    displayInviteLink,
    inviteLink: `${window.location.origin}${displayInviteLink}`,
  };
};
