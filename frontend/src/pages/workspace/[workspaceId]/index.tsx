import styled from "@emotion/styled";
import MemberGreeting from "@features/member/MemberGreeting";
import Spacing from "@primitives/layout/Spacing";
import NotionSyncCard from "@widgets/notion/NotionSyncCard";
import WorkspaceInviteMemberCard from "@widgets/workspace/WorkspaceInviteMemberCard";

/**
 * 워크스페이스 홈 화면 (`/workspace/:workspaceId`)
 *
 * 워크스페이스에 입장한 뒤의 기본 화면이다.
 * 초대 링크 복사, 노션 연동 완료, 채팅 응답 완료는 별도 라우트가 아니라
 * 이 화면 위에서 처리되는 상태 변형이다.
 *
 * GNB·사이드바·하단 Dock은 `WorkspaceLayout`이 담당하고, 이 페이지는 인사와 카드 2개를 놓기만 한다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10077 홈 화면
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10089 홈 화면/초대 링크 복사
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10101 홈 화면/노션 연동 완료
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10113 홈 화면/채팅 응답 완료
 */
export default function WorkspaceHomePage() {
  return (
    <Container>
      <MemberGreeting />
      <Spacing size={5} /> {/* 80px */}
      <ActionCards>
        <NotionSyncCard />
        <WorkspaceInviteMemberCard />
      </ActionCards>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100%;
  padding: 3.75rem 1.5rem 7rem; /* 60px 24px 112px — 아래는 하단 Dock 자리 */
`;

const ActionCards = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 1.75rem; /* 28px */
  width: 100%;
`;
