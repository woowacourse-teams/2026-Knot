import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

interface NavigateToJoinErrorParams {
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 초대 링크 오류 화면(`/join-error`)으로 이동하는 도메인 훅.
 *
 * `useEffect` 안에서 부르는 곳(초대 링크 게이트)이 있어 참조를 `useCallback`으로 고정해요.
 */
const useNavigateToJoinError = () => {
  const navigate = useNavigate();

  const navigateToJoinError = useCallback(
    ({ replace = false }: NavigateToJoinErrorParams = {}) => {
      navigate(PATH_ROUTE.JOIN_ERROR, { replace });
    },
    [navigate],
  );

  return { navigateToJoinError };
};

export default useNavigateToJoinError;
