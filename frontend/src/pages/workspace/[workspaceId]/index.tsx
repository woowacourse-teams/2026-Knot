import styled from "@emotion/styled";
import MemberGreeting from "@features/member/MemberGreeting";
import Spacing from "@primitives/layout/Spacing";
import NotionSyncCard from "@widgets/notion/NotionSyncCard";
import WorkspaceFloatingDock from "@widgets/workspace/WorkspaceFloatingDock";
import WorkspaceInviteMemberCard from "@widgets/workspace/WorkspaceInviteMemberCard";

/**
 * 워크스페이스 홈 화면 (`/workspace/:workspaceId`)
 *
 * 워크스페이스에 입장한 뒤의 기본 화면이다.
 * 초대 링크 복사, 노션 연동 완료, 채팅 응답 완료는 별도 라우트가 아니라
 * 이 화면 위에서 처리되는 상태 변형이다.
 *
 * GNB·사이드바는 `WorkspaceLayout`이 담당하고, 이 페이지는 인사·카드 2개·하단 Dock을 놓기만 한다.
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
      <DockWrapper>
        <WorkspaceFloatingDock />
      </DockWrapper>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100%;
  padding: 5rem 1.5rem 1.75rem; /* 80px 24px 28px */
`;

const ActionCards = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 1.75rem; /* 28px */
  width: 100%;
`;

/** 콘텐츠가 짧으면 바닥에, 길어져 스크롤되면 화면 하단 28px 위에 붙어 있어요. */
const DockWrapper = styled.div`
  position: sticky;
  bottom: 1.75rem; /* 28px */
  margin-top: auto;
  padding-top: 2.5rem; /* 40px */
`;
