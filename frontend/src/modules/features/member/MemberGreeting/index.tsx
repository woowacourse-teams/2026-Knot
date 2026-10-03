import useMeQuery from "@api/queries/useMeQuery";
import styled from "@emotion/styled";

/**
 * 워크스페이스 홈 상단의 인사말. 닉네임은 로그인한 회원 정보 조회(`GET /auth/me`) 응답에서 와요.
 *
 * 자세한 내용은 스토리북 `Member/MemberGreeting`에서 확인해요.
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
