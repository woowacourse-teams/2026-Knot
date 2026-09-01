import styled from "@emotion/styled";
import { useSearchParams } from "react-router";

import {
  OAUTH_ERROR_MESSAGE,
  OAUTH_ERROR_PARAM,
  OAUTH_ERROR_VALUE,
} from "./constants/oauthError";

/**
 * GitHub 로그인이 실패해 돌아왔을 때 그 사실을 알립니다.
 *
 * 백엔드는 OAuth 처리에 실패하면 로그인 화면 주소에 `?error=oauth2`를 붙여 보내요.
 * 실패 사유는 사용자가 손쓸 수 있는 것이 아니라서 구분하지 않고 한 문구로 알리고,
 * 바로 아래의 로그인 버튼으로 다시 시도하게 둡니다.
 *
 * 알릴 것이 없으면 아무것도 그리지 않아 로그인 화면의 여백이 그대로 유지돼요.
 */
export default function OAuthErrorNotice() {
  const [searchParams] = useSearchParams();

  if (searchParams.get(OAUTH_ERROR_PARAM) !== OAUTH_ERROR_VALUE) return null;

  return <Root role="alert">{OAUTH_ERROR_MESSAGE}</Root>;
}

/**
 * 실패했을 때만 자리를 차지하므로 아래 간격도 함께 가집니다.
 * 쓰는 쪽에서 `Spacing`으로 띄우면 알릴 것이 없을 때도 빈 자리가 남아요.
 */
const Root = styled.p`
  ${({ theme }) => theme.text.body02};
  width: 100%;
  margin-bottom: 0.75rem; /* 12px */
  color: ${({ theme }) => theme.sub.warning[800]};
  text-align: center;
  overflow-wrap: break-word;
`;
