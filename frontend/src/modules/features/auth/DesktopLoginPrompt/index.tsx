import styled from "@emotion/styled";

import useDesktopLoginPrompt from "./model/useDesktopLoginPrompt";

const TITLE = "GitHub 로그인";

/**
 * 데스크톱 로그인 뷰 위에 남는 띠. 로그인 중이라는 사실과 "취소"를 그려요.
 *
 * 데스크톱 셸은 GitHub 로그인을 새 창이 아니라 **앱 창 안에 뷰로** 띄우고, 위쪽 몇십 px만
 * 남겨 둡니다(기획서 5.2, 로드맵 Q68). 그 남은 자리가 이 컴포넌트예요. 높이는 셸이 알려 준
 * `headerHeight`를 그대로 쓰고, 로그인 중이 아니거나 브라우저면 아무것도 그리지 않아요.
 *
 * 화면 맨 위에 고정으로 띄우려고 `position: fixed`를 씁니다 — 뷰가 덮는 자리는 이미 셸의
 * 것이라 문서 흐름에 자리를 만들 필요가 없어요.
 */
export default function DesktopLoginPrompt() {
  const { prompt, cancel, canCancel } = useDesktopLoginPrompt();

  if (!prompt.open) return null;

  return (
    <Root style={{ height: `${prompt.headerHeight}px` }}>
      <Title>{TITLE}</Title>
      {canCancel && (
        <CancelButton type="button" onClick={cancel}>
          취소
        </CancelButton>
      )}
    </Root>
  );
}

const Root = styled.header`
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[50]};
  border-bottom: 1px solid ${({ theme }) => theme.neutral[200]};
`;

const Title = styled.span`
  ${({ theme }) => theme.text.body02};
  color: ${({ theme }) => theme.neutral[800]};
`;

const CancelButton = styled.button`
  ${({ theme }) => theme.text.body02};
  padding: 0.25rem 0.5rem; /* 4px 8px */
  color: ${({ theme }) => theme.neutral[600]};
  cursor: pointer;

  &:hover {
    color: ${({ theme }) => theme.neutral[900]};
  }
`;
