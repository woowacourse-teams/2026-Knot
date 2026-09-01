import useMeQuery from "@api/queries/useMeQuery";
import styled from "@emotion/styled";

/**
 * 워크스페이스 홈 상단의 인사말.
 *
 * 닉네임은 로그인한 회원 정보 조회(`GET /auth/me`) 응답에서 와요.
 * 응답 전에는 `반가워요`만 보여 자리를 지키고, 닉네임이 오면 `반가워요, {닉네임} 님`으로 채워요.
 * 조회 실패는 여기서 다루지 않아요. 인증이 풀린 401은 같은 화면의 워크스페이스 조회도 실패해 레이아웃이 로그인으로 보내요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10083 홈 화면 인사}
 */
export default function MemberGreeting() {
  const { data: me } = useMeQuery();

  return <Greeting>반가워요{me && `, ${me.nickname} 님`}</Greeting>;
}

const Greeting = styled.h1`
  color: ${({ theme }) => theme.neutral[800]};
  text-align: center;
  overflow-wrap: break-word;
  ${({ theme }) => theme.text.title01};
`;
