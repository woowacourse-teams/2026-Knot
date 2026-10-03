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
 * 동작 규칙은 스토리북 `Member/NicknameCard`에서 확인해요.
 */
export default function NicknameCard() {
  const {
    nickname,
    errorMessage,
    isSubmittable,
    isPending,
    inputRef,
    handleChange,
    handleSubmit,
  } = useSignUp();

  return (
    <OnboardingCard>
      <Title>닉네임</Title>
      <Spacing size={0.75} />

      <CountTextField
        ref={inputRef}
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
