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
