import useTimeout from "@hooks/common/useTimeout";
import useNavigateToJoinError from "@hooks/domain/workspace/useNavigateToJoinError";
import useNavigateToWorkspaceJoin from "@hooks/domain/workspace/useNavigateToWorkspaceJoin";
import { useEffect } from "react";
import { useParams } from "react-router";

// TODO(#243): 미리보기 API 응답을 기다리는 로딩 상태를 흉내 내고 있어요. api로 교체하면 쿼리의 loading 상태가 이 자리를 대신해요
const VERIFY_DELAY_MS = 800;
// TODO(#243): #215 `useWorkspaceInvite`의 임시 linkToken과 같은 값이에요. 미리보기 API가 붙으면 이 비교 자체를 없애요
const TEMP_VALID_LINK_TOKEN = "Xk3vQ9mZp2LrT7wB1nHc4A";
// TODO(#243): API 응답의 workspaceId로 교체
const TEMP_WORKSPACE_ID = "temp";

/**
 * 진입 직후 토큰을 한 번 판정하고 결과 화면으로 보내는 훅.
 *
 * 토큰은 BE 계약(ADR 243)대로 원문 그대로 비교하고 대문자 변환·공백 제거 같은 정규화를 하지 않아요.
 * 통과·실패 모두 `replace`로 이동해 뒤로 가기 때 이 진입 라우트로 되돌아오지 않게 해요.
 * 판정 중 페이지를 벗어나면 `useTimeout`이 타이머를 정리해 이동을 실행하지 않아요.
 *
 * TODO(#243): 미리보기 API(`GET /invitations/{tokenOrCode}`)가 붙으면
 * 아래 지연·비교를 쿼리 호출로 바꾸고 응답의 workspaceId로 이동해요.
 */
export const useWorkspaceInviteLinkGate = () => {
  const { token } = useParams();

  const { navigateToWorkspaceJoin } = useNavigateToWorkspaceJoin();
  const { navigateToJoinError } = useNavigateToJoinError();

  const { start: verify } = useTimeout({
    timeout: VERIFY_DELAY_MS,
    callback: () => {
      if (token === TEMP_VALID_LINK_TOKEN) {
        navigateToWorkspaceJoin({
          workspaceId: TEMP_WORKSPACE_ID,
          replace: true,
        });
        return;
      }

      navigateToJoinError({ replace: true });
    },
  });

  // `verify`는 useTimeout이 useCallback으로 고정해 주므로 마운트 때 한 번만 실행돼요
  useEffect(() => {
    verify();
  }, [verify]);
};
