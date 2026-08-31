import useNavigateToWorkspaceJoin from "@hooks/domain/workspace/useNavigateToWorkspaceJoin";
import { ChangeEvent, useEffect, useRef, useState } from "react";

import { INVITE_CODE_LENGTH } from "../constants/inviteCode";

export const useWorkspaceCode = () => {
  const [inputCode, setInputCode] = useState("");
  const [isVerifying, setIsVerifying] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>();

  // TODO: query 훅으로 수정하기
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const { navigateToWorkspaceJoin } = useNavigateToWorkspaceJoin();

  /**
   * 6자를 채우면 별도 트리거 없이 호출되는 임시 검증.
   *
   * TODO(#243): 미리보기 API(`GET /invitations/{tokenOrCode}`)가 붙으면
   * 아래 지연·형식 검사를 쿼리 호출로 바꾸고 응답의 workspaceId로 이동해요.
   */
  const verify = (value: string) => {
    setIsVerifying(true);

    timerRef.current = setTimeout(() => {
      timerRef.current = null;
      setIsVerifying(false);

      // TODO(#243): API 응답의 workspaceId로 교체
      if (value === "000000") {
        const TEMP_WORKSPACE_ID = "temp";
        navigateToWorkspaceJoin(TEMP_WORKSPACE_ID);
        return;
      }

      const INVITE_CODE_ERROR_MESSAGE =
        "올바르지 않은 코드예요. 다시 확인해 주세요.";

      setErrorMessage(INVITE_CODE_ERROR_MESSAGE);
    }, 800); // TODO(#243): 미리보기 API 응답을 기다리는 로딩 상태를 흉내 내고 있어요. 추후에 api로 교체하면 loading 상태일 때의 동작을 하게 돼요
  };

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (isVerifying) return;

    const nextCode = event.target.value
      .toUpperCase()
      .slice(0, INVITE_CODE_LENGTH);

    setInputCode(nextCode);
    setErrorMessage(undefined);

    if (nextCode.length === INVITE_CODE_LENGTH) verify(nextCode);
  };

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) clearTimeout(timerRef.current);
    };
  }, []);

  return {
    inputCode,
    isVerifying,
    errorMessage,
    handleChange,
  };
};
