import usePostNicknameMutation from "@api/mutations/usePostNicknameMutation";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import useNavigateToOnboardingComplete from "@hooks/domain/member/useNavigateToOnboardingComplete";
import type { ChangeEvent } from "react";
import { useState } from "react";

import { getNicknameErrorMessage } from "../utils/getNicknameErrorMessage";
import {
  getSubmitErrorMessage,
  isUnauthorizedError,
} from "../utils/getSubmitErrorMessage";

/**
 * 닉네임을 입력받아 회원가입을 완료하는 흐름.
 *
 * 에러가 두 갈래로 들어옵니다. 글자를 칠 때마다 하는 형식 검사와, 제출한 뒤 서버가 주는 응답이에요.
 * 둘을 하나의 `errorMessage`로 합쳐 입력창 아래 같은 자리에 띄웁니다.
 * 값을 다시 고치면 서버 문구는 지워요. 고친 값에 대한 판단이 아니라서요.
 *
 * 온보딩 토큰이 없거나 만료된 401은 문구로 알리지 않고 로그인 화면으로 돌려보냅니다.
 * 이 화면은 로그인을 마쳐야 의미가 있기 때문이에요.
 */
export const useSignUp = () => {
  const [nickname, setNickname] = useState("");
  const [submitErrorMessage, setSubmitErrorMessage] = useState<string>();

  const { mutate, isPending } = usePostNicknameMutation();
  const { navigateToOnboardingComplete } = useNavigateToOnboardingComplete();
  const { navigateToLogin } = useNavigateToLogin();

  const errorMessage = getNicknameErrorMessage(nickname) ?? submitErrorMessage;
  const isSubmittable = nickname.length > 0 && errorMessage === undefined;

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setNickname(event.target.value);
    setSubmitErrorMessage(undefined);
  };

  const handleSubmit = () => {
    if (!isSubmittable) return;

    mutate(
      { nickname },
      {
        onSuccess: () => navigateToOnboardingComplete({ nickname }),
        onError: (error) => {
          if (isUnauthorizedError(error)) {
            navigateToLogin({ replace: true });
            return;
          }

          setSubmitErrorMessage(getSubmitErrorMessage(error));
        },
      },
    );
  };

  return {
    nickname,
    errorMessage,
    isSubmittable,
    isPending,
    handleChange,
    handleSubmit,
  };
};
