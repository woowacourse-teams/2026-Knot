import useAcceptInvitationMutation from "@api/mutations/useAcceptInvitationMutation";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import useNavigateToJoinError from "@hooks/domain/workspace/useNavigateToJoinError";
import useNavigateToWorkspace from "@hooks/domain/workspace/useNavigateToWorkspace";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import useWorkspaceJoinState from "@hooks/domain/workspace/useWorkspaceJoinState";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import axios from "axios";
import { useEffect } from "react";

/** 초대가 없거나 만료됐거나(404) 짧은 시간에 너무 많이 시도해(429) 참여가 거절된 상태 코드 */
const JOIN_REJECTED_STATUSES = [404, 429];

const isJoinRejectedError = (error: unknown) =>
  axios.isAxiosError(error) &&
  JOIN_REJECTED_STATUSES.includes(error.response?.status ?? 0);

/**
 * 입장 확인 카드의 state 확인·참여·이동 흐름.
 *
 * 워크스페이스 이름과 참여에 쓸 credential은 미리보기를 통과한 화면이 라우터 state로 넘긴 값이에요.
 * 새로고침·주소 직접 입력처럼 state가 없으면 참여할 근거가 없으니 선택 화면(`/workspace`)으로 `replace` 이동해요.
 *
 * `참여할게요`는 state의 credential로 참여 API를 부르고, 기존 멤버십(200)·새 멤버십(201) 모두
 * 응답의 workspaceId로 홈에 가요. 요청 중에는 버튼을 로딩으로 잠가요.
 * 401은 로그인으로, 404·429는 초대 링크 오류 화면으로 `replace` 이동하고, 그 외(네트워크·5xx)는
 * 버튼을 다시 열어 재시도할 수 있게 둬요.
 */
export const useWorkspaceJoin = () => {
  const { joinState } = useWorkspaceJoinState();

  const { mutate, isPending } = useAcceptInvitationMutation();
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const { navigateToWorkspace } = useNavigateToWorkspace();
  const { navigateToJoinError } = useNavigateToJoinError();
  const { navigateToLogin } = useNavigateToLogin();

  const hasJoinState = joinState !== undefined;

  useEffect(() => {
    if (hasJoinState) return;

    navigateToWorkspace({ replace: true });
  }, [hasJoinState, navigateToWorkspace]);

  const handleJoin = () => {
    if (joinState === undefined || isPending) return;

    mutate(
      { credential: joinState.credential },
      {
        onSuccess: ({ workspaceId }) =>
          navigateToWorkspaceHome({ workspaceId: String(workspaceId) }),
        onError: (error) => {
          if (isUnauthorizedError(error)) {
            navigateToLogin({ replace: true });
            return;
          }

          if (isJoinRejectedError(error)) {
            navigateToJoinError({ replace: true });
          }
        },
      },
    );
  };

  return { workspaceName: joinState?.workspaceName, isPending, handleJoin };
};
