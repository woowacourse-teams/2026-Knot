import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

interface NavigateToLoginParams {
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 로그인 화면(`/login`)으로 이동하는 도메인 훅.
 *
 * 인증이 풀린 상태를 발견했을 때 씁니다. 되돌아올 수 없는 화면으로 보낼 때가 많아
 * `replace`를 켜면 뒤로 가기로 그 화면에 다시 들어가지 않아요.
 * `useEffect` 안에서 부르는 곳(`useWorkspaceAccessGuard`)이 있어 참조를 `useCallback`으로 고정해요.
 */
const useNavigateToLogin = () => {
  const navigate = useNavigate();

  const navigateToLogin = useCallback(
    ({ replace = false }: NavigateToLoginParams = {}) => {
      navigate(PATH_ROUTE.LOGIN, { replace });
    },
    [navigate],
  );

  return { navigateToLogin };
};

export default useNavigateToLogin;
