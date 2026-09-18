import useMeQuery from "@api/queries/useMeQuery";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
import RetryNotice from "@primitives/ui/RetryNotice";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import { Navigate, Outlet } from "react-router";

import { PATH_ROUTE } from "../PATH_ROUTE";

const AUTH_ERROR_MESSAGE = "로그인 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.";

/**
 * 로그인해야 볼 수 있는 화면들을 감싸는 가드.
 *
 * 인증 쿠키는 `httpOnly`라 자바스크립트가 읽을 수 없으므로 로그인 여부도 서버에
 * 물어봐야 합니다. 답이 오기 전에는 화면을 그리지 않아요. 먼저 그렸다가 되돌리면
 * 로그인하지 않은 사람에게 내용이 잠깐 보이기 때문입니다.
 *
 * 401은 다시 시도해도 결과가 같으므로 로그인 화면으로 보냅니다. 네트워크 오류나
 * 5xx는 로그인이 풀린 게 아니므로 로그인 화면으로 보내지 않고 다시 시도하게 둬요.
 * 여기서 내보내면 멀쩡히 로그인한 사람이 통신 한 번 실패했다고 쫓겨납니다.
 */
export default function AuthGuard() {
  const { data: me, error, refetch } = useMeQuery();

  if (isUnauthorizedError(error)) {
    return <Navigate to={PATH_ROUTE.LOGIN} replace />;
  }

  if (error) {
    return (
      <RetryNotice
        message={AUTH_ERROR_MESSAGE}
        onRetry={() => {
          void refetch();
        }}
      />
    );
  }

  if (me === undefined) {
    return <LoadingIndicator label="로그인 상태를 확인하고 있어요" />;
  }

  return <Outlet />;
}
