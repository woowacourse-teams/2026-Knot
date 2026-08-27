import styled from "@emotion/styled";
import GithubLoginButton from "@features/auth/GithubLoginButton";
import Spacing from "@primitives/layout/Spacing";
import Stack from "@primitives/layout/Stack";

/**
 * 로그인 & 회원가입 화면 (`/login`)
 *
 * 서비스 진입점이다. 신규 사용자는 로그인 후 온보딩(`/onboarding`)으로 이어진다.
 * 배경과 화면 가운데 정렬, 로고는 `CenteredLayout`이 담당한다.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=414-7 로그인 & 회원가입}
 */
export default function LoginPage() {
  return (
    <Root align="center">
      <Title>
        팀의 기록을 문서로 바꾸는
        <br />
        가장 빠른 방법
      </Title>
      <Spacing size={4.5} />

      <Actions>
        <GithubLoginButton />
        <Spacing size={1} />
        <Terms>
          로그인하면 서비스 이용약관과 개인정보 처리방침에 동의하게 됩니다.
        </Terms>
      </Actions>
    </Root>
  );
}

/**
 * 자식이 `width: 100%`로 너비를 정할 수 있도록 레이아웃의 가로를 그대로 물려받아요.
 * 이게 없으면 부모 너비가 내용에 따라 정해져서 자식의 `100%`가 기준을 잃습니다.
 */
const Root = styled(Stack)`
  width: 100%;
`;

/**
 * 버튼과 약관 문구의 너비를 함께 정합니다.
 *
 * 피그마는 360px이지만 고정하지 않아요. 화면이 좁아지면 따라 줄어들어야
 * 레이아웃이 정한 좌우 여백(24px)을 침범하지 않습니다.
 */
const Actions = styled(Stack)`
  width: 100%;
  max-width: 22.5rem; /* 360px */
`;

const Title = styled.h1`
  ${({ theme }) => theme.text.title02};
  color: ${({ theme }) => theme.neutral[800]};
  text-align: center;
`;

const Terms = styled.p`
  ${({ theme }) => theme.text.caption01};
  color: ${({ theme }) => theme.neutral[400]};
  text-align: center;
`;
