import { logoutApi } from "@api/fetch/api/v1/auth/logout";
import { useMutation } from "@tanstack/react-query";

/**
 * 로그아웃을 서버에 알리는 뮤테이션 훅.
 *
 * 서버는 204만 돌려줘요. 실제로 로그인을 끊는 일(저장한 토큰 버리기)과 화면 뒤처리는
 * `useLogout`이 맡습니다.
 */
const useLogoutMutation = () => {
  return useMutation({
    mutationFn: logoutApi,
  });
};

export default useLogoutMutation;
