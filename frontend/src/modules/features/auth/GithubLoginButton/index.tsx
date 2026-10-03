import Button from "@primitives/ui/Button";

import GithubIcon from "@/assets/icons/github.svg";

/**
 * callback(`/login/oauth2/code/github`)은 로그인 과정에서 백엔드가 쓰는 주소라 프론트가 부르지 않습니다.
 */
const GITHUB_OAUTH_URL = `${process.env.API_BASE_URL}/oauth2/authorization/github`;

/**
 * GitHub 계정으로 로그인을 시작하는 버튼.
 *
 * 동작 규칙은 스토리북 `Auth/GithubLoginButton`에서 확인해요.
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
