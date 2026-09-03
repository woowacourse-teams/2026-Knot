import useInvitationPreviewQuery from "@api/queries/useInvitationPreviewQuery";
import useNavigateToJoinError from "@hooks/domain/workspace/useNavigateToJoinError";
import useNavigateToWorkspaceJoin from "@hooks/domain/workspace/useNavigateToWorkspaceJoin";
import { useEffect } from "react";
import { useParams } from "react-router";

/**
 * 진입 직후 토큰을 미리보기로 한 번 판정하고 결과 화면으로 보내는 훅.
 *
 * 토큰은 BE 계약(ADR 243)대로 원문 그대로 보내고 대문자 변환·공백 제거 같은 정규화를 하지 않아요.
 * 통과하면 응답의 workspaceId로 입장 확인 화면에 가면서 토큰과 워크스페이스 이름을 라우터 state로 넘기고,
 * 실패(404·429·네트워크·5xx 모두)하면 초대 링크 오류 화면으로 보내요.
 * 통과·실패 모두 `replace`로 이동해 뒤로 가기 때 이 진입 라우트로 되돌아오지 않게 해요.
 * 판정 중 페이지를 벗어나면 effect가 돌지 않아 이동을 실행하지 않아요.
 *
 * effect deps에 응답 DTO 대신 `workspaceId`·`workspaceName` 원시값을 두는 이유는
 * 쿼리 `data`가 refetch마다 새 참조라서예요(`query-hooks.md`).
 */
export const useWorkspaceInviteLinkGate = () => {
  const { token } = useParams();

  const { navigateToWorkspaceJoin } = useNavigateToWorkspaceJoin();
  const { navigateToJoinError } = useNavigateToJoinError();

  const { data: preview, isError } = useInvitationPreviewQuery({
    credential: token ?? "",
    enabled: token !== undefined,
  });

  const workspaceId = preview?.workspaceId;
  const workspaceName = preview?.workspaceName;

  useEffect(() => {
    if (
      token === undefined ||
      workspaceId === undefined ||
      workspaceName === undefined
    ) {
      return;
    }

    navigateToWorkspaceJoin({
      workspaceId: String(workspaceId),
      credential: token,
      workspaceName,
      replace: true,
    });
  }, [token, workspaceId, workspaceName, navigateToWorkspaceJoin]);

  useEffect(() => {
    if (!isError) return;

    navigateToJoinError({ replace: true });
  }, [isError, navigateToJoinError]);
};
