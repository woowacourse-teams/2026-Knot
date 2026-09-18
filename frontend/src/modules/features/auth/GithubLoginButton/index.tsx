import Button from "@primitives/ui/Button";

import GithubIcon from "@/assets/icons/github.svg";

/**
 * GitHub OAuth 진입점.
 *
 * 백엔드가 이 주소를 받으면 GitHub 로그인 페이지로 다시 보내고,
 * 로그인이 끝나면 callback까지 처리한 뒤 쿠키를 심어 프론트로 돌려보내요.
 * `/login/oauth2/code/github`는 그 과정에서 백엔드가 쓰는 주소라 프론트가 부르지 않습니다.
 */
const GITHUB_OAUTH_URL = `${process.env.API_BASE_URL}/oauth2/authorization/github`;

/**
 * GitHub 계정으로 로그인을 시작하는 버튼.
 *
 * `httpClient`가 아니라 `window.location.href`로 **페이지를 통째로 이동**시켜요.
 * 이 주소는 응답을 받아 화면에 그리는 API가 아니라 GitHub으로 가는 302 리다이렉트라,
 * axios로 부르면 GitHub 도메인에서 CORS에 막히고 쿠키도 제대로 심기지 않습니다.
 *
 * 이동 뒤의 흐름은 백엔드가 정합니다.
 * 기존 회원은 접근 토큰 쿠키를 받고, 신규 사용자는 온보딩 토큰 쿠키를 받아
 * 닉네임 입력 화면(`/onboarding`)으로 돌아옵니다.
 *
 * 서버가 허용한 출처에서만 동작하므로 `localhost`에서는 확인할 수 없어요.
 * 배포된 주소에서 확인해야 합니다.
 */
export default function GithubLoginButton() {
  const handleClick = () => {
    window.location.href = GITHUB_OAUTH_URL;
  };

  return (
    <Button size="lg" isFullWidth onClick={handleClick}>
      <GithubIcon />
      GitHub으로 시작하기
    </Button>
  );
}
