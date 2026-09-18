import useStartNotionOAuthMutation from "@api/mutations/useStartNotionOAuthMutation";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import useNavigateToWorkspace from "@hooks/domain/workspace/useNavigateToWorkspace";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import axios from "axios";
import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router";

import {
  NOTION_CONNECTION_RESULT,
  NOTION_CONNECTION_RESULT_PARAM,
} from "../constants/notionConnect";
import { getConnectErrorMessage } from "../utils/getConnectErrorMessage";

/** 없는 워크스페이스라 연결을 시작할 수 없는 상태 코드 */
const isNotFoundError = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 404;

/**
 * 노션 연동 카드의 연결 시작·결과 처리·건너뛰기 흐름.
 *
 * `노션 연결하기`는 연결 시작 API로 Notion 인증 URL을 받아 페이지를 통째로 그 주소로 옮겨요.
 * axios로 Notion에 갈 수 없어(CORS) `window.location.assign`을 쓰고, 이동이 끝날 때까지
 * 버튼을 로딩으로 붙잡아 두 번 눌리지 않게 합니다.
 *
 * Notion에서 돌아오면 서버가 `?result=connected|failed`를 붙여 이 화면으로 보내요.
 * `connected`면 워크스페이스 홈으로 `replace` 이동하고, `failed`면 `isFailed`를 켜
 * 카드가 실패 화면(워크스페이스로 이동)을 그리게 둡니다. 그 밖의 알 수 없는 값은
 * 잘못된 접근으로 보고 쿼리를 지워(`replace`) 연결 카드로 되돌립니다.
 *
 * 연결 시작 실패는 401 → 로그인, 404 → 워크스페이스 선택 화면으로 `replace` 이동하고,
 * 403(OWNER 아님)과 그 외는 버튼 아래 문구로 알립니다.
 */
export const useNotionConnect = () => {
  const { workspaceId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const result = searchParams.get(NOTION_CONNECTION_RESULT_PARAM);
  const isConnected = result === NOTION_CONNECTION_RESULT.connected;
  const isFailed = result === NOTION_CONNECTION_RESULT.failed;
  const isUnknownResult = result !== null && !isConnected && !isFailed;

  const [errorMessage, setErrorMessage] = useState<string>();
  const [isRedirecting, setIsRedirecting] = useState(false);

  const { mutate, isPending } = useStartNotionOAuthMutation();
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const { navigateToWorkspace } = useNavigateToWorkspace();
  const { navigateToLogin } = useNavigateToLogin();

  const isConnecting = isPending || isRedirecting;

  useEffect(() => {
    if (!isConnected || workspaceId === undefined) return;

    navigateToWorkspaceHome({ workspaceId, replace: true });
  }, [isConnected, workspaceId, navigateToWorkspaceHome]);

  useEffect(() => {
    if (!isUnknownResult) return;

    setSearchParams({}, { replace: true });
  }, [isUnknownResult, setSearchParams]);

  const handleConnect = () => {
    if (workspaceId === undefined || isConnecting) return;

    setErrorMessage(undefined);
    mutate(Number(workspaceId), {
      onSuccess: ({ authorizationUrl }) => {
        setIsRedirecting(true);
        window.location.assign(authorizationUrl);
      },
      onError: (error) => {
        if (isUnauthorizedError(error)) {
          navigateToLogin({ replace: true });
          return;
        }

        if (isNotFoundError(error)) {
          navigateToWorkspace({ replace: true });
          return;
        }

        setErrorMessage(getConnectErrorMessage(error));
      },
    });
  };

  const handleGoHome = () => {
    if (workspaceId === undefined) return;

    navigateToWorkspaceHome({ workspaceId });
  };

  return { isFailed, isConnecting, errorMessage, handleConnect, handleGoHome };
};
