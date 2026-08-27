import styled from "@emotion/styled";

import GithubIcon from "@/assets/icons/github.svg";
import Spacing from "@primitives/layout/Spacing";
import Stack from "@primitives/layout/Stack";
import Button from "@primitives/ui/Button";

/**
 * 로그인 & 회원가입 화면 (`/login`)
 *
 * 서비스 진입점이다. 신규 사용자는 로그인 후 온보딩(`/onboarding`)으로 이어진다.
 * 배경과 화면 가운데 정렬은 `CenteredLayout`이 담당한다.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=414-7 로그인 & 회원가입}
 */
export default function LoginPage() {
  return (
    <Stack align="center">
      {/* 로고는 CenteredLayout이 그립니다. 그 아래 간격만 여기서 정해요. */}
      <Spacing size={2.5} />

      <Title>
        팀의 기록을 문서로 바꾸는
        <br />
        가장 빠른 방법
      </Title>
      <Spacing size={4.5} />

      {/* TODO: GitHub OAuth 연결 */}
      <LoginButton size="lg">
        <GithubIcon />
        GitHub으로 시작하기
      </LoginButton>
      <Spacing size={1} />

      <Terms>
        로그인하면 서비스 이용약관과 개인정보 처리방침에 동의하게 됩니다.
      </Terms>
    </Stack>
  );
}

const Title = styled.h1`
  ${({ theme }) => theme.text.title02};
  color: ${({ theme }) => theme.neutral[800]};
  text-align: center;
`;

const LoginButton = styled(Button)`
  width: 22.5rem; /* 360px */
`;

/** 문구가 길어지면 360px에서 줄이 바뀌도록 상한만 둡니다. */
const Terms = styled.p`
  ${({ theme }) => theme.text.caption01};
  max-width: 22.5rem; /* 360px */
  color: ${({ theme }) => theme.neutral[400]};
  text-align: center;
`;
