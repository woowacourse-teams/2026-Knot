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
 * 동작 규칙은 스토리북 `Auth/OAuthErrorNotice`에서 확인해요.
 */
export default function OAuthErrorNotice() {
  const [searchParams] = useSearchParams();

  if (searchParams.get(OAUTH_ERROR_PARAM) !== OAUTH_ERROR_VALUE) return null;

  return <Root role="alert">{OAUTH_ERROR_MESSAGE}</Root>;
}

const Root = styled.p`
  ${({ theme }) => theme.text.body02};
  width: 100%;
  margin-bottom: 0.75rem; /* 12px */
  color: ${({ theme }) => theme.sub.warning[800]};
  text-align: center;
  overflow-wrap: break-word;
`;
