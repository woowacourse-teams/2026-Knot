import useMeQuery from "@api/queries/useMeQuery";
import styled from "@emotion/styled";

/**
 * 워크스페이스 홈 상단의 인사말.
 *
 * 동작 규칙은 스토리북 `Member/MemberGreeting`에서 확인해요.
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
