import useMeQuery from "@api/queries/useMeQuery";
import styled from "@emotion/styled";
import Avatar from "@primitives/ui/Avatar";

import useMemberProfileMenu from "./model/useMemberProfileMenu";

/**
 * 로그인한 회원의 프로필 아바타와 계정 메뉴.
 *
 * GitHub OAuth로 받아 둔 프로필 이미지를 그리고, 아직 응답이 오기 전이거나 이미지가 없으면
 * 닉네임 첫 글자로 대신해요. 누르면 계정 메뉴가 열리고, 항목은 로그아웃과
 * 데스크톱 앱에서만 보이는 `CLI 에이전트 연결`·`Claude 구독`이에요.
 *
 * 로그아웃은 웹의 유일한 진입점입니다. 데스크톱 앱은 앱 메뉴에도 같은 항목을 두지만
 * 실제로 부르는 것은 이 화면과 같은 액션이에요(기획서 5.1).
 * `CLI 에이전트 연결`은 로컬 MCP 서버 상태와 CLI 등록 스니펫을 보여 주는 데스크톱 전용
 * 화면(`/agent-connection`)으로 가요(기획서 6.4). `Claude 구독`은 내 구독 로그인·모델 설정
 * 화면(`/claude-subscription`)으로 가요(기획서 6.5).
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516 Avatar size=32}
 */
export default function MemberProfileMenu() {
  const { data: me } = useMeQuery();
  const {
    isOpen,
    toggleMenu,
    containerRef,
    logout,
    isLoggingOut,
    hasAgentConnection,
    goToAgentConnection,
    hasClaudeSubscription,
    goToClaudeSubscription,
  } = useMemberProfileMenu();

  return (
    <Container ref={containerRef}>
      <Trigger
        type="button"
        aria-label="내 계정 메뉴"
        aria-haspopup="menu"
        aria-expanded={isOpen}
        onClick={toggleMenu}
      >
        <Avatar
          label="내 프로필"
          src={me?.profileImageUrl}
          name={me?.nickname}
          size={32}
        />
      </Trigger>

      {isOpen && (
        <Menu role="menu">
          {hasAgentConnection && (
            <MenuItem
              type="button"
              role="menuitem"
              onClick={goToAgentConnection}
            >
              CLI 에이전트 연결
            </MenuItem>
          )}
          {hasClaudeSubscription && (
            <MenuItem
              type="button"
              role="menuitem"
              onClick={goToClaudeSubscription}
            >
              Claude 구독
            </MenuItem>
          )}
          <MenuItem
            type="button"
            role="menuitem"
            disabled={isLoggingOut}
            onClick={logout}
          >
            로그아웃
          </MenuItem>
        </Menu>
      )}
    </Container>
  );
}

const Container = styled.div`
  position: relative; /* 메뉴가 아바타 바로 아래에 붙어요 */
  display: flex;
`;

const Trigger = styled.button`
  display: flex;
  border-radius: 50%;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.neutral[400]};
    outline-offset: 2px;
  }
`;

const Menu = styled.div`
  position: absolute;
  top: calc(100% + 0.5rem); /* 8px */
  right: 0;
  z-index: 1; /* 본문 위에 떠요 */
  display: flex;
  flex-direction: column;
  min-width: 8rem; /* 128px */
  padding: 0.25rem; /* 4px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const MenuItem = styled.button`
  padding: 0.5rem 0.75rem; /* 8px 12px */
  border-radius: 0.5rem; /* 8px */
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[800]};
  text-align: left;
  cursor: pointer;

  &:hover:enabled {
    background-color: ${({ theme }) => theme.neutral[100]};
  }

  &:disabled {
    color: ${({ theme }) => theme.neutral[400]};
    cursor: default;
  }
`;
