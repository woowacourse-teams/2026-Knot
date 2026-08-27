import styled from "@emotion/styled";
import Spacing from "@primitives/layout/Spacing";
import Button from "@primitives/ui/Button";
import CountTextField from "@primitives/ui/CountTextField";
import OnboardingCard from "@primitives/ui/OnboardingCard";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useState } from "react";
import { useNavigate } from "react-router";

import { getNicknameErrorMessage } from "./utils/getNicknameErrorMessage";

const NICKNAME_MAX_LENGTH = 20;

/**
 * 닉네임을 입력받아 회원 정보를 등록하는 카드.
 *
 * 입력값과 형식 검사를 스스로 들고 있어서 놓기만 하면 동작해요.
 * 등록이 끝나면 가입 완료(`/onboarding/complete`)로 이동하며,
 * 인사말에 쓸 닉네임을 라우터 state로 함께 넘깁니다.
 *
 * 형식 검사는 클라이언트에서 하지만 중복 검사는 서버만 알 수 있으므로,
 * 등록 API가 나오면 응답 에러를 `errorMessage`에 함께 흘려보내야 해요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-390 닉네임 입력 전}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-628 닉네임 입력 중}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1237 닉네임 입력 에러}
 */
export default function NicknameCard() {
  const [nickname, setNickname] = useState("");
  const navigate = useNavigate();

  const errorMessage = getNicknameErrorMessage(nickname);
  const isSubmittable = nickname.length > 0 && errorMessage === undefined;

  const handleSubmit = () => {
    // TODO: 닉네임 등록 API 연결. 성공 응답을 받은 뒤 이동하도록 바꿉니다.
    navigate(PATH_ROUTE.ONBOARDING_COMPLETE, { state: { nickname } });
  };

  return (
    <OnboardingCard>
      <Title>닉네임</Title>
      <Spacing size={0.75} />

      {/* TODO: 서버 중복 검사 붙이기. 형식 검사는 클라이언트에서 처리합니다 */}
      <CountTextField
        value={nickname}
        onChange={(event) => setNickname(event.target.value)}
        maxLength={NICKNAME_MAX_LENGTH}
        placeholder="닉네임"
        errorMessage={errorMessage}
      />
      <Spacing size={1.5} />

      <Button
        size="lg"
        isFullWidth
        disabled={!isSubmittable}
        onClick={handleSubmit}
      >
        확인
      </Button>
    </OnboardingCard>
  );
}

const Title = styled.h1`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.neutral[900]};
`;
