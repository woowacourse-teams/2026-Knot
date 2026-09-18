import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import useNavigateToWorkspace from "@hooks/domain/workspace/useNavigateToWorkspace";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import axios from "axios";
import { useEffect } from "react";

// TODO: api 내부 구현(401·403·404)이 노출됨. 수정 필요

interface UseWorkspaceAccessGuardParams {
  /** 워크스페이스 관련 쿼리의 에러. 없으면 아무것도 하지 않아요. */
  error: unknown;
}

/** 멤버가 아니거나(403) 없는 워크스페이스(404)라 들어갈 수 없는 상태 코드 */
const ACCESS_DENIED_STATUSES = [403, 404];

const isAccessDeniedError = (error: unknown) =>
  axios.isAxiosError(error) &&
  ACCESS_DENIED_STATUSES.includes(error.response?.status ?? 0);

/**
 * 워크스페이스 조회 실패를 한 곳에서 판정해 이동시키는 도메인 훅.
 *
 * - 401: 인증이 풀린 상태라 로그인 화면으로 `replace` 이동해요.
 * - 403·404: 멤버가 아니거나 없는 워크스페이스라 선택 화면(`/workspace`)으로 `replace` 이동해요.
 *
 * 그 외(네트워크·5xx·timeout)는 다루지 않아요. 진입을 막을 근거가 아니라서요.
 * 쓰는 쪽은 에러를 넘기기만 하고, 어디로 보낼지는 여기서 정합니다.
 *
 * effect deps의 `navigateToLogin`·`navigateToWorkspace`는 각 훅이 `useCallback`으로 고정한 참조라,
 * 판정 결과가 바뀔 때만 한 번 이동해요. 이동 뒤에도 살아 있는 컴포넌트(레이아웃 가드)에 붙여도 반복되지 않아요.
 */
const useWorkspaceAccessGuard = ({ error }: UseWorkspaceAccessGuardParams) => {
  const { navigateToLogin } = useNavigateToLogin();
  const { navigateToWorkspace } = useNavigateToWorkspace();

  const isUnauthorized = isUnauthorizedError(error);
  const isAccessDenied = isAccessDeniedError(error);

  useEffect(() => {
    if (isUnauthorized) {
      navigateToLogin({ replace: true });
      return;
    }

    if (isAccessDenied) {
      navigateToWorkspace({ replace: true });
    }
  }, [isUnauthorized, isAccessDenied, navigateToLogin, navigateToWorkspace]);
};

export default useWorkspaceAccessGuard;
