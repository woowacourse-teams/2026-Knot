import Button from "@primitives/ui/Button";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

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
 * **지금은 온보딩 화면으로 바로 넘어갑니다.** 로그인 뒤 백엔드가 어느 주소로
 * 돌려보내는지 아직 정해지지 않아, 화면 흐름만 먼저 이어두었어요.
 *
 * 주소가 정해지면 `handleClick`의 `navigate`를 아래 한 줄로 바꾸면 됩니다.
 *
 * ```ts
 * window.location.href = GITHUB_OAUTH_URL;
 * ```
 *
 * `httpClient`가 아니라 `window.location.href`인 이유가 있어요. 이 주소는 응답을 받아
 * 화면에 그리는 API가 아니라 GitHub으로 가는 302 리다이렉트라, axios로 부르면 GitHub
 * 도메인에서 CORS에 막히고 쿠키도 제대로 심기지 않습니다.
 *
 * 이동 뒤의 흐름은 백엔드가 정합니다. 기존 회원은 Access Token 쿠키를,
 * 신규 사용자는 온보딩 토큰 쿠키를 받고 각각 다른 화면으로 돌아옵니다.
 */
export default function GithubLoginButton() {
  const navigate = useNavigate();

  const handleClick = () => {
    // TODO: 백엔드 redirect 주소가 정해지면 GITHUB_OAUTH_URL로 이동하도록 교체
    navigate(PATH_ROUTE.ONBOARDING);
  };

  return (
    <Button size="lg" isFullWidth onClick={handleClick}>
      <GithubIcon />
      GitHub으로 시작하기
    </Button>
  );
}
