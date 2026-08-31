import { PATH_ROUTE } from "@routes/PATH_ROUTE";

/**
 * 참여 코드로 팀원에게 보낼 초대 링크를 만들어요.
 *
 * 링크를 열면 초대 코드 입력 화면(`/workspace/code`)에 `?code=`가 채워진 채로 들어갑니다.
 * 이 형식은 UI 단계의 임시 결정이라, 초대 API를 붙일 때 BE linkToken 사용 여부와 함께 다시 정해요.
 */
export const getInviteLink = (code: string) => ({
  displayInviteLink: `${PATH_ROUTE.WORKSPACE_CODE}?code=${encodeURIComponent(code)}`,
  inviteLink: `${window.location.origin}${PATH_ROUTE.WORKSPACE_CODE}?code=${encodeURIComponent(code)}`,
});
