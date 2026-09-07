import { clearAccessToken } from "@api/authToken";
import useLogoutMutation from "@api/mutations/useLogoutMutation";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import { useQueryClient } from "@tanstack/react-query";

/**
 * 로그아웃하고 로그인 화면으로 보내는 도메인 훅.
 *
 * 로그인 상태는 우리가 저장해 둔 액세스 토큰이 전부라, 그 토큰을 버리는 것이 곧 로그아웃이에요.
 * 서버에는 알리기만 하고(204), 응답을 기다렸다가 토큰을 지운 뒤 남은 흔적(캐시)을 비우고 화면을 옮깁니다.
 *
 * 요청이 실패해도 같은 뒤처리를 합니다. 토큰을 지우는 쪽은 우리라서 서버 응답과 무관하게 로그아웃은 성립하고,
 * 화면만 로그인한 것처럼 남는 쪽이 더 나빠요.
 *
 * 토큰을 다 지운 뒤에 화면을 옮깁니다. 먼저 옮기면 로그인 화면의 `GuestGuard`가 아직 살아 있는 토큰으로
 * 로그인 상태를 확인해 다시 안으로 들여보내요.
 *
 * 뒤로 가기로 방금 나온 화면에 돌아가지 못하도록 히스토리를 대체(`replace`)해요.
 */
const useLogout = () => {
  const queryClient = useQueryClient();
  const { navigateToLogin } = useNavigateToLogin();
  const { mutate, isPending } = useLogoutMutation();

  const logout = () => {
    mutate(undefined, {
      onSettled: async () => {
        await clearAccessToken();
        // 다음 사람이 로그인했을 때 앞사람의 응답이 잠깐 보이지 않도록 통째로 비워요
        queryClient.clear();
        navigateToLogin({ replace: true });
      },
    });
  };

  return { logout, isLoggingOut: isPending };
};

export default useLogout;
