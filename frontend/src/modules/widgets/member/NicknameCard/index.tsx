import styled from "@emotion/styled";
import Spacing from "@primitives/layout/Spacing";
import Button from "@primitives/ui/Button";
import CountTextField from "@primitives/ui/CountTextField";
import OnboardingCard from "@primitives/ui/OnboardingCard";

import { NICKNAME_MAX_LENGTH } from "./constants/nickname";
import { useSignUp } from "./model/useSignUp";

/**
 * 닉네임을 입력받아 회원가입을 완료하는 카드.
 *
 * GitHub 로그인을 마친 신규 사용자가 도착하는 화면이에요. 닉네임을 등록해야
 * 회원가입이 끝나고 서버가 접근 토큰을 발급합니다.
 *
 * 입력값과 제출은 `useSignUp`이 맡고, 이 파일은 화면만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-390 닉네임 입력 전}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-628 닉네임 입력 중}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1237 닉네임 입력 에러}
 */
export default function NicknameCard() {
  const {
    nickname,
    errorMessage,
    isSubmittable,
    isPending,
    handleChange,
    handleSubmit,
  } = useSignUp();

  return (
    <OnboardingCard>
      <Title>닉네임</Title>
      <Spacing size={0.75} />

      <CountTextField
        value={nickname}
        onChange={handleChange}
        maxLength={NICKNAME_MAX_LENGTH}
        placeholder="닉네임"
        errorMessage={errorMessage}
        aria-label="닉네임"
        autoComplete="off"
        autoFocus
      />
      <Spacing size={1.5} />

      <Button
        size="lg"
        isFullWidth
        disabled={!isSubmittable}
        isLoading={isPending}
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
