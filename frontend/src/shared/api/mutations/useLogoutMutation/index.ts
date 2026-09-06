import { logoutApi } from "@api/fetch/api/v1/auth/logout";
import { useMutation } from "@tanstack/react-query";

/**
 * 로그인 세션을 끝내는 뮤테이션 훅.
 *
 * 성공하면 서버가 인증 쿠키를 만료시키므로 이후 요청은 전부 401이 돼요. 화면에 남아 있는
 * 캐시를 비우고 로그인 화면으로 보내는 뒤처리는 `useLogout`이 맡습니다.
 */
const useLogoutMutation = () => {
  return useMutation({
    mutationFn: logoutApi,
  });
};

export default useLogoutMutation;
