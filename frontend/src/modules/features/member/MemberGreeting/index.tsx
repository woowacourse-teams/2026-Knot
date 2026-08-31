import styled from "@emotion/styled";

import { MEMBER_NICKNAME } from "./constants/member";

/**
 * 워크스페이스 홈 상단의 인사말.
 *
 * 회원 정보 API가 아직 없어 닉네임은 임시 상수예요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10083 홈 화면 인사}
 */
export default function MemberGreeting() {
  return <Greeting>반가워요, {MEMBER_NICKNAME} 님</Greeting>;
}

const Greeting = styled.h1`
  color: ${({ theme }) => theme.neutral[800]};
  text-align: center;
  overflow-wrap: break-word;
  ${({ theme }) => theme.text.title01};
`;
