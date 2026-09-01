import useInvitationPreviewQuery from "@api/queries/useInvitationPreviewQuery";
import useTimeout from "@hooks/common/useTimeout";
import useNavigateToWorkspaceJoin from "@hooks/domain/workspace/useNavigateToWorkspaceJoin";
import { ChangeEvent, useEffect, useState } from "react";

import { INVITE_CODE_LENGTH } from "../constants/inviteCode";
import { getPreviewErrorMessage } from "../utils/getPreviewErrorMessage";

/** 검증을 통과한 뒤 성공 상태를 보여주는 시간. 지나면 입장 확인 화면으로 이동해요. */
const SUCCESS_DISPLAY_MS = 1500;

/**
 * 초대 코드를 입력받아 미리보기로 검증하고 입장 확인 화면으로 넘기는 흐름.
 *
 * 6자를 채우면 별도 트리거 없이 미리보기 쿼리가 켜져요. 요청 중(`isVerifying`)에는 입력을 잠그고,
 * 성공하면 1.5초 동안 성공 상태(`isVerified`)를 보여준 뒤 응답의 workspaceId로 이동하면서
 * 입력한 코드와 워크스페이스 이름을 라우터 state로 넘겨요.
 *
 * 실패 문구는 쿼리 에러에서 바로 만들어요. 값을 고치면 쿼리 키가 바뀌어 문구도 함께 사라지고,
 * 같은 코드를 다시 채우면 마운트 때 다시 확인하므로 별도 초기화가 없어요.
 * 성공 표시 중에 페이지를 벗어나면 `useTimeout`이 타이머를 정리해 이동을 실행하지 않아요.
 */
export const useWorkspaceCode = () => {
  const [inputCode, setInputCode] = useState("");

  const { navigateToWorkspaceJoin } = useNavigateToWorkspaceJoin();

  const isCodeComplete = inputCode.length === INVITE_CODE_LENGTH;
  const {
    data: preview,
    isFetching: isVerifying,
    isSuccess,
    error,
  } = useInvitationPreviewQuery({
    credential: inputCode,
    enabled: isCodeComplete,
  });

  /** 검증을 통과하면 1.5초 동안 성공 상태를 보여준 뒤 입장 확인 화면으로 이동. */
  const { isTimedOut: isVerified, start: showSuccess } = useTimeout({
    timeout: SUCCESS_DISPLAY_MS,
    callback: () => {
      if (preview === undefined) return;

      navigateToWorkspaceJoin({
        workspaceId: String(preview.workspaceId),
        credential: inputCode,
        workspaceName: preview.workspaceName,
      });
    },
  });

  // 캐시된 응답이 다시 확인되는 동안(isFetching)에는 성공 표시를 미뤄요
  useEffect(() => {
    if (!isSuccess || isVerifying) return;

    showSuccess();
  }, [isSuccess, isVerifying, showSuccess]);

  const errorMessage = error ? getPreviewErrorMessage(error) : undefined;

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (isVerifying || isVerified) return;

    setInputCode(event.target.value.toUpperCase().slice(0, INVITE_CODE_LENGTH));
  };

  return {
    inputCode,
    isVerifying,
    isVerified,
    errorMessage,
    handleChange,
  };
};
