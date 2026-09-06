import useLogoutMutation from "@api/mutations/useLogoutMutation";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import { useQueryClient } from "@tanstack/react-query";

/**
 * 로그아웃하고 로그인 화면으로 보내는 도메인 훅.
 *
 * 로그인 상태는 `httpOnly` 쿠키라 자바스크립트가 지울 수 없어서, 서버에 로그아웃을
 * 요청하는 것 말고는 세션을 끊을 방법이 없어요. 그래서 화면에서 하는 일은 요청을 보내고
 * 남은 흔적(캐시)을 비운 뒤 로그인 화면으로 옮기는 것뿐입니다.
 *
 * 요청이 실패해도 같은 뒤처리를 합니다. 서버는 응답을 만들기 전에 쿠키를 먼저 만료시키므로
 * 실패로 보이는 응답(리다이렉트·네트워크 오류) 뒤에도 세션은 이미 끊겨 있을 수 있고,
 * 그때 화면만 로그인한 것처럼 남는 쪽이 더 나빠요. 로그인 화면으로 갔는데 세션이 살아 있으면
 * `GuestGuard`가 다시 안으로 들여보냅니다.
 *
 * 뒤로 가기로 방금 나온 화면에 돌아가지 못하도록 히스토리를 대체(`replace`)해요.
 */
const useLogout = () => {
  const queryClient = useQueryClient();
  const { navigateToLogin } = useNavigateToLogin();
  const { mutate, isPending } = useLogoutMutation();

  const logout = () => {
    mutate(undefined, {
      onSettled: () => {
        // 다음 사람이 로그인했을 때 앞사람의 응답이 잠깐 보이지 않도록 통째로 비워요
        queryClient.clear();
        navigateToLogin({ replace: true });
      },
    });
  };

  return { logout, isLoggingOut: isPending };
};

export default useLogout;
