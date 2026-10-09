import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import { useEffect } from "react";

interface UseRedirectToLoginOnUnauthorizedParams {
  /** 조회의 에러. 없으면 아무것도 하지 않아요. */
  error: unknown;
}

/**
 * 로그인이 풀린 실패(401)면 로그인 화면으로 `replace` 이동하는 도메인 훅.
 *
 * 쓰는 쪽은 에러를 넘기기만 해요. 이동하는 동안 오류 안내를 그리지 않도록 `isUnauthorized`를 돌려줘요.
 * 워크스페이스 조회처럼 403·404까지 판정해 이동해야 하면 `useWorkspaceAccessGuard`를 써요.
 *
 * 이동하면 사라지는 화면 안에서 쓰는 것을 전제로 해요. `BrowserRouter`에서는 주소가 바뀔 때마다 `navigateToLogin` 참조가
 * 새로 만들어져 effect가 다시 돌기 때문에, 이동한 뒤에도 남는 곳(레이아웃)에 붙이면 401인 동안 주소가 바뀔 때마다 다시 로그인으로 보내요.
 */
const useRedirectToLoginOnUnauthorized = ({
  error,
}: UseRedirectToLoginOnUnauthorizedParams) => {
  const { navigateToLogin } = useNavigateToLogin();

  const isUnauthorized = isUnauthorizedError(error);

  useEffect(() => {
    if (isUnauthorized) navigateToLogin({ replace: true });
  }, [isUnauthorized, navigateToLogin]);

  return { isUnauthorized };
};

export default useRedirectToLoginOnUnauthorized;
