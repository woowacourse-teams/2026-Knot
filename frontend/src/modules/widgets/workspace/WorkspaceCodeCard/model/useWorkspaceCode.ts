import useTimeout from "@hooks/common/useTimeout";
import useNavigateToWorkspaceJoin from "@hooks/domain/workspace/useNavigateToWorkspaceJoin";
import { ChangeEvent, useState } from "react";

import { INVITE_CODE_LENGTH } from "../constants/inviteCode";

// TODO(#243): 미리보기 API 응답을 기다리는 로딩 상태를 흉내 내고 있어요. 추후에 api로 교체하면 loading 상태일 때의 동작을 하게 돼요
const VERIFY_DELAY_MS = 800;
/** 검증을 통과한 뒤 성공 상태를 보여주는 시간. 지나면 입장 확인 화면으로 이동해요. */
const SUCCESS_DISPLAY_MS = 1500;

export const useWorkspaceCode = () => {
  const [inputCode, setInputCode] = useState("");
  const [errorMessage, setErrorMessage] = useState<string>();

  const { navigateToWorkspaceJoin } = useNavigateToWorkspaceJoin();

  /** 검증을 통과하면 1.5초 동안 성공 상태를 보여준 뒤 입장 확인 화면으로 이동. */
  const { isTimedOut: isVerified, start: showSuccess } = useTimeout({
    timeout: SUCCESS_DISPLAY_MS,
    callback: () => {
      // TODO(#243): API 응답의 workspaceId로 교체
      const TEMP_WORKSPACE_ID = "temp";
      navigateToWorkspaceJoin({ workspaceId: TEMP_WORKSPACE_ID });
    },
  });

  /**
   * 6자를 채우면 별도 트리거 없이 호출되는 임시 검증.
   *
   * TODO(#243): 미리보기 API(`GET /invitations/{tokenOrCode}`)가 붙으면
   * 아래 지연·형식 검사를 쿼리 호출로 바꾸고 응답의 workspaceId로 이동해요.
   */
  const { isTimedOut: isVerifying, start: verify } = useTimeout({
    timeout: VERIFY_DELAY_MS,
    callback: () => {
      // 임시로 000000을 통과 코드로 설정
      if (inputCode === "000000") {
        showSuccess();
        return;
      }

      const INVITE_CODE_ERROR_MESSAGE =
        "올바르지 않은 코드예요. 다시 확인해 주세요.";

      setErrorMessage(INVITE_CODE_ERROR_MESSAGE);
    },
  });

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (isVerifying || isVerified) return;

    const nextCode = event.target.value
      .toUpperCase()
      .slice(0, INVITE_CODE_LENGTH);

    setInputCode(nextCode);
    setErrorMessage(undefined);

    if (nextCode.length === INVITE_CODE_LENGTH) verify();
  };

  return {
    inputCode,
    isVerifying,
    isVerified,
    errorMessage,
    handleChange,
  };
};
